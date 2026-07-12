# Deferred Lights

Deferred lights are colored lights you can move, dim, and remove cheaply. You can have a lot of them on screen at once.

Positions and ranges are in world blocks. Colors are linear RGB from 0 to 1, though you can push past 1 if you want blooming. Angles are in degrees. A light goes live the moment the factory returns it; there's no `start()` and no `add()`. Call `remove()` to drop it permanently, or `setEnabled(false)` to mute it without losing the handle.

---

## How the light pass works

This is a screen-space deferred pass. Understanding the model explains both what works and what doesn't.

Amnetic composites in two stages, split by whether an effect lights surfaces or fills the air:

- **Surface stage** at `END_MAIN` (under translucents): gbuffer fill, instanced meshes, models, decals, the [Shadows](Shadows) bake, the surface lighting pass, [SSAO/SSGI](Screen-Space-Effects), and SSR. Water and glass correctly draw over lit opaque geometry.
- **Atmosphere stage** at the `renderLevel` TAIL (`AmneticClient.renderPost()`, over everything including translucents, clouds, and weather): the volumetric god-ray pass, [Bloom](Bloom), and colour grade.

The deferred pass runs twice per frame: once for surface lighting (no god-rays) in the surface stage, then again outputting only god-rays in the atmosphere stage. For the current frame the surface light pass:

1. Copies the main framebuffer's color and depth into its own capture target.
2. Packs visible lights into a GPU buffer, each stored camera-relative (world position minus camera position) so the shader works in single-precision near the origin.
3. Reconstructs every pixel's world position from the copied depth and the inverse view-projection matrix.
4. Reads the pixel's surface normal and material from the G-buffer when it's populated. Where it isn't (vanilla terrain and anything else that doesn't write the G-buffer), it estimates a normal from the depth neighbors instead (it picks the closer horizontal and vertical neighbor so silhouettes don't smear), then flips it to face the camera.
5. Sums each light's contribution (direction to the light, N·L, distance falloff, the cone term for spots, and shadow visibility for casters), multiplies by the pixel's albedo (the copied color), and writes the recomposed scene back onto the main framebuffer with blending *disabled*. It's a replace, not an add: the shader outputs the captured scene darkened by sun-shadow occlusion plus local lights plus specular, and additive blending can't darken, which a daylight directional sun needs.

What gets lit is whatever wrote depth into the main target: vanilla terrain, entities, and Amnetic models. The sky is skipped; any pixel at the far plane gets no light.

What this means in practice:

Vanilla surfaces get reconstructed normals. Amnetic geometry that fills the G-buffer is lit with its real normals and material; everything else falls back to a depth-derived normal, which is a solid geometric normal for flat and curved surfaces but knows nothing about normal maps or fine surface detail.

By default a light reaches every surface within range that faces it, walls in between included. Opt a light into [shadows](Shadows) with `castsShadow(true)` (plus the global `Shadows.enable()`) and the pass samples that light's shadow map, so occluders actually block it. The sun path can even darken the vanilla daylight, which is why the pass replaces the scene rather than purely adding to it.

There's no occlusion field. This version reconstructs from depth and normals only and doesn't build or sample a separate AO volume.

One pass, capped count. Every light is evaluated per pixel in a single fullscreen draw, so cost scales with light count times screen pixels. That's what the budget and culling settings in `LightSettings` are for.

If Iris is loaded, the pass bails out entirely. A shaderpack runs its own lighting pipeline and Amnetic stays out of the way.

The pass also guards itself against errors: a throwing frame logs a warning and skips that frame, and after 60 consecutive failures the pass disables itself rather than hammering a broken frame. It isn't dead forever, though; if shader hot reload delivers new shader source, the pass automatically re-enables and tries again.

The capture target follows screen size, and the whole pass only runs when you're in a world with at least one light enabled.

---

## Light types

Pick a type by calling the matching factory. Each type uses a different subset of parameters; setting a parameter a type ignores just does nothing.

### `POINT` (`Lights.point`)

An omnidirectional bulb. Lights everything within `range` that faces it. Uses position, color, range, intensity, and falloff.

### `SPOT` (`Lights.spot`)

A cone from a point. Uses everything POINT does, plus direction (where the cone points) and the spot angles (inner and outer cone). Inside the inner cone it's full brightness; between inner and outer it falls to zero. Good for flashlights, headlights, and projector beams.

### `DIRECTIONAL` (`Lights.directional`)

A parallel fill from infinitely far away, like a sun or moon. Uses only direction, color, and intensity. It tints every lit surface by N·L against the incoming direction. Use it sparingly as a global mood fill.

### `AREA_RECT` (`Lights.areaRect`)

A glowing rectangle. Uses position (the panel center), direction (the panel's facing normal), tangent (its "right" axis), and area size as half-extents (`halfW`, `halfH`). The shader finds the closest point on the rectangle to each surface and lights from there. Good for ceiling panels, monitors, windows, and light boxes.

### `AREA_DISC` (`Lights.areaDisc`)

A glowing disc. Like `AREA_RECT` but circular: position (center), direction (facing normal), and area size where the width doubles as the radius.

### `TUBE` (`Lights.tube`)

A glowing line segment, like a fluorescent tube or neon strip. Uses position (the center of the segment), tangent (the axis the tube runs along), and tube length. The shader lights from the closest point on the segment. Good for strip lighting, lightsabers, and neon signs.

---

## Falloff curves

`FalloffCurve` controls how a light fades from full at distance 0 to nothing at `range`. It applies to every type except `DIRECTIONAL`. Distance is normalized as `t = clamp(dist / range, 0, 1)`.

`SMOOTH` is the default. It's `1 - smoothstep(t)`, an S-curve that stays bright for a while, eases off, and lands softly at the range edge.

`LINEAR` is just `1 - t`, a straight ramp from full to zero.

`INVERSE_SQUARE` is physically flavored `1/d²`, windowed so it actually reaches zero at `range` instead of trailing off forever. Bright and punchy near the source, with a long dim tail.

`EXPONENT` computes `(1 - t)^param`, where `param` is the exponent you pass to `setFalloff(curve, param)`. Higher exponents concentrate the light near the source; lower ones spread it out. The exponent defaults to `2` and is clamped to a minimum of `0.01`. Use this when you want to dial the curve by hand.

```java
light.setFalloff(FalloffCurve.INVERSE_SQUARE); // punchy bulb
light.setFalloff(FalloffCurve.EXPONENT, 4f); // tight, fast fade
```

---

## Creating lights

Every factory takes color as three linear floats (`r`, `g`, `b` from 0..1), and most take `range` in blocks and `intensity` as a plain radiance multiplier where 1 is the baseline. Positions are `Vec3` in world blocks; directions and tangents are `Vector3f` and get normalized for you.

```java
import com.meekdev.amnetic.client.light.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// a warm point light, 16-block reach, 1.5x intensity
Light lamp = Lights.point(new Vec3(100, 64, 200), 1f, 0.7f, 0.3f, 16f, 1.5f);

// a spotlight: position, direction, inner/outer cone (deg), color, range, intensity
Light beam = Lights.spot(eye, dir, 12f, 25f, 1f, 1f, 0.8f, 20f, 2f);

// a sun-like directional fill (no position, no range)
Light sun = Lights.directional(new Vector3f(-0.3f, -1f, -0.2f), 1f, 1f, 0.9f, 0.6f);

// a ceiling panel: center, facing normal, half-width, half-height, color, range, intensity
Light panel = Lights.areaRect(new Vec3(8, 70, 8), new Vector3f(0, -1, 0), 2f, 1f, 1f, 1f, 1f, 12f, 1.5f);

// a glowing disc: center, normal, radius, color, range, intensity
Light disc = Lights.areaDisc(new Vec3(8, 65, 8), new Vector3f(0, 1, 0), 1.5f, 0.4f, 0.7f, 1f, 14f, 2f);

// a neon strip: center, tangent (the axis it runs along), length, color, range, intensity
Light neon = Lights.tube(new Vec3(8, 66, 8), new Vector3f(1, 0, 0), 4f, 1f, 0.2f, 0.6f, 10f, 2.5f);
```

---

## The `Light` setters

Everything on `Light` chains, so you can keep tuning after creation. Each setter clamps or normalizes where it makes sense.

`setPosition(Vec3)` / `setPosition(double x, y, z)` sets the world position in blocks. Defaults to `(0,0,0)`. Ignored by DIRECTIONAL.

`setDirection(Vector3f)` / `setDirection(float dx, dy, dz)` sets the primary direction: the spot/cone axis, the area-light facing normal, or the directional incoming direction. Normalized for you; a near-zero vector is ignored. Defaults to `(0,-1,0)` (straight down).

`setTangent(float tx, ty, tz)` sets the "right" axis for AREA_RECT and the running axis for TUBE. Normalized; near-zero is ignored. Defaults to `(1,0,0)`.

`setColor(float r, g, b)` sets linear RGB. No upper clamp, so values above 1 are allowed and read as overbright. Defaults to `(1,1,1)`.

`setTemperature(float kelvin)` sets color from a black-body temperature instead of RGB. Clamped to 1000..40000 K. Roughly: 2000 K is candle-orange, 3000 K warm white, 5500 K daylight, 7000 K and up a cool blue-white. This overwrites whatever `setColor` had.

`setRange(float)` sets reach in blocks. Clamped to >= 0. Defaults to 12. Ignored by DIRECTIONAL.

`setIntensity(float)` sets the radiance multiplier. Clamped to >= 0. Defaults to 1.

`setLumens(float)` is an alternative to `setIntensity`; it sets `intensity = lumens / 100`, clamped to >= 0. So `setLumens(150)` equals `setIntensity(1.5)`.

`setSpotAngles(float innerDeg, float outerDeg)` sets the spot cone in degrees, stored internally as cosines. The outer angle is forced to be at least the inner angle. Defaults to inner 12°, outer 25°. Only SPOT uses these.

`setFalloff(FalloffCurve)` sets the curve and keeps the current exponent.

`setFalloff(FalloffCurve, float param)` sets the curve and the EXPONENT exponent at once. The exponent is clamped to >= 0.01. Default curve is SMOOTH, default exponent 2.

`setAreaSize(float w, float h)` sets half-extents for AREA_RECT and radius (via width) for AREA_DISC. Both clamped to >= 0. Defaults to `1, 1`.

`setTubeLength(float)` sets the length of a TUBE segment in blocks. Clamped to >= 0. Defaults to 2.

`setEnabled(boolean)` mutes or unmutes without removing. Defaults to true.

Read-only accessors mirror the state: `type()`, `x()`/`y()`/`z()`, `dirX()`/`dirY()`/`dirZ()`, `tanX()`/`tanY()`/`tanZ()`, `red()`/`green()`/`blue()`, `range()`, `intensity()`, `cosInner()`/`cosOuter()`, `falloffId()`/`falloffParam()`, `areaW()`/`areaH()`, `tubeLen()`, and `isEnabled()`.

```java
lamp.setColor(0.2f, 0.6f, 1f)
    .setRange(24f)
    .setIntensity(3f)
    .setFalloff(FalloffCurve.INVERSE_SQUARE);

lamp.setTemperature(3000f); // warm white from kelvin instead of RGB
```

---

## Lifecycle

Lights are live on creation. The factory registers them and the pass picks them up the next frame. There's nothing to start.

`light.remove()` drops the light from the registry permanently. After removal `isEnabled()` reports false even if the flag was true, and calling `remove()` again is a no-op. Use this when the light is gone for good.

`light.setEnabled(false)` keeps the handle and the registration but skips the light during packing. Flip it back with `setEnabled(true)`. Use this for lights that blink, gate on game state, or pool and reuse.

`Lights.clear()` removes every light at once.

Amnetic also calls `Lights.clear()` for you at two boundaries: every server disconnect (so lights never leak from one world into the next) and client shutdown, where the `CLIENT_STOPPING` hook clears the registry and disposes the GPU resources. You're still responsible for removing lights you no longer want during a session, and for re-creating your lights when the player joins a new world.

To move a light, just set its position each frame from wherever you drive your logic:

```java
lamp.setPosition(entity.position().add(0, 1.5, 0));
```

---

## `LightSettings`: budget, culling, beams

`LightSettings.defaults()` returns the single global config. Tune it once at startup; the values are read every frame by the pass and the packer. All setters chain.

`maxLights(int)` is the hard cap on how many lights the pass evaluates, clamped to >= 1. Default is 256. This sizes the GPU buffer when the pass first initializes, so set it before the first light is drawn; raising it later in a session won't grow an already-allocated buffer. Lights past the cap during packing are simply skipped.

`frustumCull(boolean)` defaults to true. When on, any light whose bounding sphere (its position and range) is fully off-screen gets skipped that frame. DIRECTIONAL lights are never culled. Leave this on unless you're debugging a light that's vanishing at screen edges.

`lodFade(float startBlocks, float endBlocks)` handles distance fade for lights that are far past their useful reach. The two numbers are blocks past the light's range, measured from the camera. A light fades linearly from full at `range + startBlocks` to fully culled at `range + endBlocks`. Defaults are 64 and 128. `startBlocks` is clamped to >= 0 and `endBlocks` is forced to be at least `startBlocks + 1`. The fade scales the light's intensity, so distant lights die out gracefully instead of popping. DIRECTIONAL lights are never faded.

`volumetric(boolean)` turns on god-rays. Off by default. When on, the pass marches the view ray through each non-directional light's range (ray-sphere clipped) and accumulates single-scattering in the air. It's the most expensive feature here; treat it as a look decision. An unoccluded point light reads as a glowing sphere, so use a spot light with occluders if you want beam-like shafts.

`volumetricSteps(int)` sets the march steps per pixel, clamped to 0..64. Default 16. The getter returns 0 whenever volumetric is off.

`volumetricScale(float)` sets the resolution the god-ray march renders at, relative to the screen, clamped to 0.25..1. Default 0.5: the scatter is computed at half resolution and then upscaled depth-aware onto the main target, which is most of what keeps the feature affordable. Raise it toward 1.0 only if you can see the upscale.

`volumetricTemporal(boolean)` defaults to true. The half-res scatter is reprojected against the previous frame and accumulated before the upscale, which smooths the stepping noise from the low step count. Turn it off if you'd rather have raw (noisier but lag-free) rays.

Each light can also override the march for its own shaft: `godray(float)` scales its scatter (0 kills the shaft), `godraySteps(int)` (0..64, default 16), `godrayDensity(float)` (>= 0, default 0.4), `godrayAniso(float)` (0..0.95, default 0.6), and `godrayShadows(boolean)` (default true) tune that light without touching the globals. See the `Light` reference below.

`volumetricStrength(float)` sets the overall scatter scale, >= 0. Default 1.0.

`volumetricDensity(float)` sets fog density along the ray, >= 0. Default 0.4.

`volumetricAniso(float)` sets Henyey-Greenstein anisotropy (forward glow), clamped 0..0.95. Default 0.6.

`volumetricShadows(boolean)` defaults to true. God-ray steps sample the light's [shadow map](Shadows) (cheap single tap), so beams are carved by occluders. Requires the light to have `castsShadow(true)`.

`contactShadows(boolean)` runs a screen-space depth raymarch per light that kills residual light-leak at contacts. Off by default; applies to every light.

`contactSteps(int)` (0..64, default 12), `contactDistance(float)` (>= 0.01, blocks, default 0.5), and `contactThickness(float)` (>= 0.01, blocks, default 0.5) control the contact-shadow march. `contactSteps()` returns 0 while contactShadows is off.

`cookieTexture(Identifier)` sets a global texture projected through any spot light flagged `cookie(true)` (gobo). Unset by default.

```java
LightSettings.defaults()
    .maxLights(512)
    .frustumCull(true)
    .lodFade(48f, 96f); // start fading 48 blocks past range, gone by 96
```

---

## Worked examples

### A flickering torch attached to an entity

A warm point light that moves with an entity and flickers. Create it once, then update its position and intensity every frame from your render loop.

```java
import com.meekdev.amnetic.client.light.*;
import net.minecraft.world.phys.Vec3;

// once, when the torch entity spawns
Light torch = Lights.point(entity.position().add(0, 1.2, 0),
        1f, 0.6f, 0.25f, // warm orange
        14f, // range
        2.0f); // base intensity
torch.setFalloff(FalloffCurve.INVERSE_SQUARE);

// every frame, while the entity is alive
double t = System.nanoTime() * 1e-9;
float flicker = 1.7f + 0.3f * (float) Math.sin(t * 23.0) * (float) Math.sin(t * 7.0);
torch.setPosition(entity.position().add(0, 1.2, 0))
     .setIntensity(flicker);

// when the entity dies
torch.remove();
```

### A colored spotlight

A tight beam aimed down a corridor. The narrow inner cone keeps a hard hot spot; the wider outer cone gives it a soft edge.

```java
import org.joml.Vector3f;

Light spot = Lights.spot(
        new Vec3(12, 70, 30), // position
        new Vector3f(0, -0.4f, 1f), // aim: forward and slightly down
        8f, 22f, // inner 8 deg, outer 22 deg
        0.5f, 0.7f, 1f, // cool blue-white
        28f, // range
        3f); // intensity
spot.setFalloff(FalloffCurve.EXPONENT, 3f); // tight, fast fade

// re-aim it later; it normalizes for you
spot.setDirection(new Vector3f(0.2f, -0.5f, 1f));
```

### A tuned settings block for a lamp-heavy scene

Lots of small local lights, aggressive culling and fade, and a touch of volumetrics for atmosphere. Set this before your lights start drawing.

```java
LightSettings.defaults()
    .maxLights(384) // headroom for a busy build
    .frustumCull(true) // drop off-screen lights
    .lodFade(32f, 80f) // retire lights soon after they leave their reach
    .volumetric(true) // experimental beams
    .volumetricSteps(20)
    .volumetricStrength(0.5f);
```

---

## Reference

### `Lights`

```java
Light point(Vec3 pos, float r, float g, float b, float range, float intensity);
Light spot(Vec3 pos, Vector3f dir, float innerDeg, float outerDeg,
           float r, float g, float b, float range, float intensity);
Light directional(Vector3f dir, float r, float g, float b, float intensity);
Light areaRect(Vec3 pos, Vector3f normal, float halfW, float halfH,
               float r, float g, float b, float range, float intensity);
Light areaDisc(Vec3 pos, Vector3f normal, float radius,
               float r, float g, float b, float range, float intensity);
Light tube(Vec3 pos, Vector3f tangent, float len,
           float r, float g, float b, float range, float intensity);
void clear(); // remove every light
```

### `Light` (all setters chain)

```java
Light setPosition(Vec3 pos);   Light setPosition(double x, double y, double z);
Light setDirection(Vector3f dir);   Light setDirection(float dx, float dy, float dz); // normalized
Light setTangent(float tx, float ty, float tz); // normalized
Light setColor(float r, float g, float b); // linear RGB, 0..1 baseline (over 1 allowed). default 1,1,1
Light setTemperature(float kelvin); // clamped 1000..40000; overwrites color
Light setRange(float range); // blocks, >= 0. default 12
Light setIntensity(float intensity); // >= 0. default 1
Light setLumens(float lumens); // intensity = lumens / 100, >= 0
Light setSpotAngles(float innerDeg, float outerDeg); // degrees, stored as cosines. default 12 / 25
Light setFalloff(FalloffCurve curve);
Light setFalloff(FalloffCurve curve, float param); // EXPONENT exponent, >= 0.01. default curve SMOOTH, param 2
Light setAreaSize(float w, float h); // rect half-extents / disc radius (w). >= 0. default 1,1
Light setTubeLength(float len); // blocks, >= 0. default 2
Light setEnabled(boolean enabled); // default true

// shadows / extras (spot & point unless noted) -- see the Shadows page
Light castsShadow(boolean casts); // opt this light into shadow casting. default false
Light shadowStrength(float strength); // 0..1, partial shadow. default 1
Light cookie(boolean on); // project LightSettings.cookieTexture (spot lights). default false
Light iesProfile(int profile); // 0 = none, 1..5 analytic angular curves. default 0
Light godray(float strength); // per-light volumetric scatter multiplier, >= 0. default 1
Light godraySteps(int steps); // per-light march steps, 0..64. default 16
Light godrayDensity(float d); // per-light fog density, >= 0. default 0.4
Light godrayAniso(float g); // per-light Henyey-Greenstein g, 0..0.95. default 0.6
Light godrayShadows(boolean on); // shaft samples this light's shadow map. default true

LightType type();   long id();   boolean isEnabled();   void remove();

// read-only accessors
double x(); double y(); double z();
float dirX(); float dirY(); float dirZ();
float tanX(); float tanY(); float tanZ();
float red(); float green(); float blue();
float range(); float intensity();
float cosInner(); float cosOuter();
int falloffId(); float falloffParam();
float areaW(); float areaH(); float tubeLen();
boolean castsShadow(); float shadowStrength();
boolean cookie(); int iesProfile();
float godray(); int godraySteps(); float godrayDensity();
float godrayAniso(); boolean godrayShadows();
```

### `LightType`

```java
POINT // omnidirectional bulb. position, color, range, intensity, falloff
SPOT // cone. POINT + direction + spot angles
DIRECTIONAL // parallel sun/moon fill. direction, color, intensity only; no range/falloff/cull/fade
AREA_RECT // glowing rectangle. position, direction (normal), tangent, area half-extents
AREA_DISC // glowing disc. position, direction (normal), area width = radius
TUBE // glowing line segment. position, tangent, tube length
```

### `FalloffCurve`

```java
SMOOTH // 1 - smoothstep(t); soft S-curve. the default
LINEAR // 1 - t; straight ramp
INVERSE_SQUARE // windowed 1/d^2; punchy near source, reaches zero at range
EXPONENT // (1 - t)^param; param is the exponent from setFalloff(curve, param)
```

### `LightSettings`

```java
static LightSettings defaults(); // the one global config

LightSettings maxLights(int max); // >= 1. default 256. allocated once on first draw
LightSettings frustumCull(boolean on); // default true. directional never culled
LightSettings lodFade(float startBlocks, float endBlocks);
//   blocks PAST a light's range: fade from range+start to range+end. default 64 / 128.
//   start >= 0; end forced to >= start + 1. directional never faded.
LightSettings volumetric(boolean on); // god-rays. default false
LightSettings volumetricSteps(int steps); // clamped 0..64. default 16. getter returns 0 if volumetric off
LightSettings volumetricStrength(float s); // >= 0. default 1.0
LightSettings volumetricScale(float s); // march resolution, clamp [0.25, 1]. default 0.5 (half res)
LightSettings volumetricTemporal(boolean on); // reproject + accumulate the scatter. default true
LightSettings volumetricDensity(float d); // >= 0. default 0.4
LightSettings volumetricAniso(float g); // 0..0.95. default 0.6 (Henyey-Greenstein forward glow)
LightSettings volumetricShadows(boolean on); // default true. needs the light's castsShadow(true)
LightSettings contactShadows(boolean on); // screen-space. default false
LightSettings contactSteps(int steps); // 0..64. default 12. getter returns 0 if contactShadows off
LightSettings contactDistance(float blocks); // >= 0.01. default 0.5
LightSettings contactThickness(float blocks); // >= 0.01. default 0.5
LightSettings cookieTexture(Identifier id); // global gobo for spot lights with cookie(true)

// getters
int maxLights(); boolean frustumCull();
float lodFadeStart(); float lodFadeEnd();
boolean volumetric(); int volumetricSteps(); float volumetricStrength();
float volumetricScale(); boolean volumetricTemporal();
float volumetricDensity(); float volumetricAniso(); boolean volumetricShadows();
boolean contactShadows(); int contactSteps(); float contactDistance(); float contactThickness();
Identifier cookieTexture();
```

---

## See Also

- [Bloom](Bloom). Runs after the light pass; push light intensity or color past 1 to make fixtures glow.
- [Models](Models). Amnetic models draw into color and depth just before this pass, so they're lit by your custom lights too.
- [Framebuffers](Framebuffers). The capture-and-reconstruct machinery the light pass is built on.