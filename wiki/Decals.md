# Decals

Decals are textures projected onto existing world geometry: bullet holes, scorch marks, graffiti, blood splatter, moss. You don't build any geometry for them. You place a box in the world, and whatever surfaces fall inside that box get the texture stamped onto them, terrain, entities, and Amnetic models alike.

```java
import com.meekdev.amnetic.client.decal.Decals;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// a 2x2 block scorch mark on the floor at (100, 64, 200)
Decal scorch = Decals.box(
        Identifier.fromNamespaceAndPath("mymod", "textures/decal/scorch.png"),
        new Vec3(100, 64.01, 200), // box center
        new Vector3f(0, 1, 0), // facing: sticks to upward-facing surfaces
        2f, 2f, 0.5f); // width, height, depth (projection thickness)

scorch.opacity(0.9f).tint(0.2f, 0.2f, 0.2f);

// later
scorch.remove();
```

That's the whole lifecycle. A decal is live the moment `Decals.box(...)` returns; there is no `start()` and nothing to register. Amnetic drives the decal pass itself every frame, so you never call `Decals.render()` yourself unless you're replacing the default pipeline. Call `remove()` to drop a decal permanently, or `Decals.clear()` to drop them all.

---

## How the decal pass works

Decals are screen-space projected. The renderer never draws a quad in the world. Instead, each decal is one fullscreen pass that:

1. Copies the main framebuffer's depth into a capture target.
2. Reconstructs every pixel's world position from that depth and the inverse view-projection matrix.
3. Transforms the position into the decal's local space (the box maps to `[-0.5, 0.5]` on each axis) and discards any pixel outside the box.
4. Projects the texture along the box's facing axis: the local X/Z coordinates become the texture UVs.
5. Estimates the surface normal at the pixel from depth neighbors and fades the decal where the surface doesn't face the decal's axis.
6. Alpha-blends the result onto the main framebuffer.

Because it works from the depth buffer, a decal wraps over whatever is actually there: steps, slabs, entity silhouettes, curved model surfaces. Sky pixels (depth at the far plane) are always skipped.

### Projection box semantics

The box is defined by a center (`Vec3`, world blocks), a facing `normal`, and three sizes:

- **`width`** spans the box's local right axis.
- **`height`** spans the box's local up axis.
- **`depth`** spans the facing normal, i.e. how far the projection reaches into and out of the surface.

The normal is the surface orientation the decal sticks to: `(0, 1, 0)` for a floor decal, `(0, 0, -1)` for a north-facing wall, and so on. The in-plane right axis is derived as `normal x (0, 1, 0)` (falling back to `(1, 0, 0)` when the normal is vertical), and up completes the basis. All sizes are full extents, so a `width` of 2 covers 2 blocks.

Keep `depth` modest. It's the tolerance for how far a surface can sit off the box's midplane and still receive the decal; a huge depth on a floor decal will also stamp the ceiling if the box reaches it. The decal fades out smoothly over the outer 30 percent of the depth range, so surfaces near the front or back face of the box get a soft edge rather than a hard clip.

### Angle fade

`angleFade` controls how much a surface may deviate from the decal's facing before the decal disappears. The shader computes the dot product between the reconstructed surface normal and the decal normal, then fades with `smoothstep(angleFade, 1.0, dot)`. At the default `0.3`, surfaces facing the decal get full strength and the decal dies off on steep or perpendicular geometry, which prevents the classic smearing artifact where a floor decal stretches down the side of a step. Lower values are more permissive (more wrap, more stretch); `1.0` restricts the decal to surfaces almost exactly aligned with the normal.

---

## Creating and tuning decals

`Decals.box` is the only factory. Every setter on the returned `Decal` chains, and everything is live: change a field and the next frame renders it.

```java
Decal d = Decals.box(tex, center, new Vector3f(0, 1, 0), 2f, 2f, 0.5f)
        .opacity(0.8f) // overall alpha multiplier, clamped 0..1. default 1
        .angleFade(0.4f) // surface-alignment cutoff, clamped 0..1. default 0.3
        .tint(0.3f, 0.3f, 0.3f);// multiplied into the texture RGB. default white

// move / reshape it later
d.center(new Vec3(101, 64.01, 200))
 .normal(0, 1, 0)
 .width(3f).height(3f).depth(0.4f);
```

`tint` multiplies the texture color, so white shows the texture as-is and darker tints work well for scorch and grime. Sizes clamp to `>= 0`; a zero-length normal is ignored rather than applied.

`remove()` flags the decal and drops it from the active list permanently. There is no disable toggle; if you want a decal to blink, remove and respawn it, or drive `opacity` to 0 (an opacity-0 decal still runs its fullscreen pass, so prefer `remove()` for anything long-lived).

---

## Textures

The texture is any resource `Identifier` pointing at a PNG, e.g. `minecraft:textures/block/mud.png` or `mymod:textures/decal/crack.png`. The renderer registers and loads it on first use; you don't preload anything. Textures imported through the editor's texture importer work too.

Sampling is nearest-filtered and clamped to edge, so decal textures stay crisp and never tile or bleed at the border. The texture's alpha channel is the decal's shape: transparent texels leave the surface untouched.

If the texture can't be resolved (or you passed `null`), the plain path falls back to a procedural soft disc in the tint color, a radial fade that reads as a generic scorch or blob. Handy for prototyping before art exists.

---

## The two shader paths

Every decal renders its color through `decal.fsh`, the plain path described above: texture times tint, alpha-blended onto the main target. Since decals draw before the deferred lighting pass, that color becomes part of the albedo the light pass reads, so plain decals are lit like the surface they sit on. For most stains and marks this is all you need.

A decal can additionally be **relightable**: as soon as you give it a normal map or a roughness override, it also writes the [G-buffer](Render-Pipeline), and deferred lighting treats the decaled pixels as their own material.

```java
Decals.box(albedo, pos, new Vector3f(0, 1, 0), 2f, 2f, 0.3f)
     .normalMap(Identifier.fromNamespaceAndPath("mymod", "textures/decal/crack_n.png"))
     .roughness(0.4f)
     .metallic(0.1f);
```

Relightable decals run a second pass through `decal_gbuffer.fsh`, after the color pass. It writes into the gbuffer's normal and material attachments only (never albedo, never depth), using the same box test and angle fade as the color pass, with `opacity * fade` as the blend weight. The normal map is sampled in the decal's tangent space (right/up/normal basis) with its X/Y components boosted 3x for punchier relief; without a normal map the decal writes its flat facing normal. The material write carries roughness and metallic with material id 0 (default PBR).

The rules of the relightable path:

- `writesGBuffer()` becomes true when `normalMap` is set or `roughness >= 0`. Roughness uses `-1` as the "don't override" sentinel; set `roughness(-1f)` and clear the normal map to turn the gbuffer write back off.
- If a decal is relightable through its normal map alone, the material write uses a roughness fallback of `0.7`.
- The pass requires the gbuffer to be populated this frame (the normal-fill pass ran). If deferred lighting isn't active, the gbuffer pass silently skips and you keep the plain color result.
- `metallic` clamps to `0..1` and only matters on relightable decals; the plain path ignores it.

---

## Multiply decals

`multiply(true)` switches a decal from alpha blending to multiplying the surface: the shader outputs `mix(1, texture * tint, alpha)` and the pass blends with `DST_COLOR, ZERO`. The surface keeps its own lighting, so a stain in an unlit cave stays dark instead of glowing at full texture brightness. It works with the relightable path too, which is how a wet splat can multiply in its colour and still give the surface a glossy roughness.

```java
Decals.box(splat, hit, wallNormal, 0.3f, 0.3f, 0.25f)
     .multiply(true) // darken the wall by the texture, don't paint over it
     .roughness(0.08f); // and make it glossy while it's wet
```

---

## Film decals

A film decal covers a box of floor with a grid of texels that you write from the CPU every tick. Each texel describes a thin layer of liquid and what it leaves behind, and the shader turns that into a surface: soft, noisy outlines; Beer-Lambert tint, so thin films show the floor through and deep pools go nearly opaque; Fresnel reflections; a GGX sun glint; height-field normals with a rounded rim; ripples while it flows; parallax depth; and dried residue with a darker coffee ring. Blood, oil, mud, spilled paint and puddles all fit.

```java
FilmLook blood = new FilmLook()
        .absorption(480f, 1600f, 1400f) // per world unit of thickness
        .agedAbsorption(800f, 1300f, 1600f) // blended in by the age channel
        .stainTint(0.45f, 0.22f, 0.17f) // what dried residue multiplies the surface by
        .maxThickness(0.03f) // the 8-bit field's range, sqrt encoded
        .edge(0.0011f, 0.00045f) // thickness where the outline sits, softness
        .roughness(0.05f, 0.8f); // wet, dry

// one film per 16x16 block area and floor level, 8 texels per block
FilmDecal film = Decals.film(x, y - 0.06, z, 16f, 1.04f, 16f, 128, 128, blood);

// every tick, for the texels that changed
film.texel(tx, tz, floorY, thickness, wetness, stain, age, flow);
film.light(tx, tz, skyLight / 15f, blockLight / 15f);

// what wet films reflect, update it with the time of day
Decals.filmEnvironment(sunX, sunY, sunZ, sunR, sunG, sunB, skyR, skyG, skyB, torchR, torchG, torchB);
```

### The field

Each texel carries two RGBA8 values, uploaded row-range by row-range only where they changed:

- **A**: thickness (sqrt-encoded against `maxThickness`), wetness, stain, flow.
- **B**: floor height inside the box, age, sky light, block light.

A is bilinear filtered. The shader builds outlines from a 2x2 tent of half-texel taps, so a field written per cell still gives round contours instead of stair steps. B is filtered for age and light, but the floor height is read exact with `texelFetch` from the four nearest texels; 0 means unset. A pixel only receives the film when its depth-reconstructed height matches one of those floors within a few centimetres and its surface faces up. Steps, walls, table tops and the feet of whoever is standing in the pool stay clean, and the film never z-fights because it isn't geometry.

Texels under a wall next to a wet texel should copy the wet value. The floor mask keeps the copy off the wall, and without it the filtering thins the pool exactly where it meets the wall and leaves a dry sliver.

### Lighting

The colour pass uses dual-source blending: `result = added + destination * transmittance`, per channel. Transmittance is `exp(-absorption * thickness)` mixed by coverage, times the stain tint, minus what the wet surface reflects. Added light is the in-scatter colour, plus the environment reflection weighted by Fresnel, plus the sun glint, all scaled by the texel's light levels so pools in the dark stay dark.

A second pass writes the gbuffer normal and roughness where the film is wet, weighted by wet coverage. Deferred lights then glint on it (a torch, a camera flash) and SSR reflects the room in it. Dry residue doesn't touch the gbuffer, so it keeps the surface's own material.

### Cost

Each film is one scissored fullscreen triangle for colour and one for the gbuffer, and the scissor is the screen rect of its box. Films off screen or empty are skipped. Per pixel it's about 25 texture fetches and a small fbm, cheap next to SSR. A 16x16 film at 128x128 is 128 KB of CPU memory and two small textures.

### Pixel art mode

`FilmLook.pixelArt(true)` swaps the smooth liquid for pixel art that sits inside a blocky texture world:

- **Snapped to the world.** The film is evaluated once per world texel (`texelsPerUnit`, 16 matches Minecraft's block textures). The grid is anchored to the film's origin, so its pixels line up with the block pixels underneath and never swim when the camera moves.
- **No sim grid in the shapes.** The cell field is rebuilt as a smooth Catmull-Rom surface from a 6x6 cell window and only then cut at texel centres. One cleanup pass follows: a texel with fewer than 2 straight neighbours goes, a gap with 3 or more fills. Edges come out as even stair steps. This needs `texelsPerUnit` to be at least twice the field's cells per unit.
- **`pixelStyle(jitter, outline, core, highlights)`:**
  - `jitter` (0..1) is a fixed, world-stable per-texel shade noise between close shades, like vanilla textures.
  - `outline` is 0 for none, 1 for a darker edge on the shadow side only, 2 for an outline all around.
  - `core` darkens the thick parts.
  - `highlights` (0..1) puts sparse lit texels just inside the lit edge, but only where the sun, block light or one of the 4 nearest Amnetic lights (a camera flash counts) reaches.
- **`palette(...)`**, 8 colours as 0xRRGGBB:
  - 0: highlight
  - 1: light body
  - 2: body
  - 3: dark body
  - 4: edge
  - 5: aged body
  - 6: aged dark and dried edge
  - 7: dried stain
- **`pattern(wet, dry)`:** two tiling textures, painted one texel per world texel, with the texture wrapping in world space. When set, they replace the palette shades: wet for fresh liquid, dry for aged liquid and stains. The pool shape cuts them, the shadow-side edge uses palette colour 4 (6 for stains), and the thinnest film drops texels so the floor shows through. Resource packs can replace the textures.
- **Vanilla-style light.** Sky and block light on whole levels through vanilla's curve, with no Fresnel, no SSR and no gbuffer write.

### API

```java
static FilmDecal film(double x, double minY, double z, float sizeX, float sizeY, float sizeZ, int resX, int resZ, FilmLook look);
static List<FilmDecal> films();
static void filmEnvironment(float sunX, float sunY, float sunZ, float sunR, float sunG, float sunB,
                            float skyR, float skyG, float skyB, float blockR, float blockG, float blockB);
static void clearFilms(); // also runs on disconnect

FilmDecal texel(int x, int z, float floorY, float thickness, float wetness, float stain, float age, float flow);
FilmDecal light(int x, int z, float sky, float block);
FilmDecal clearTexel(int x, int z);
FilmDecal clearAll();
FilmDecal look(FilmLook look); // thickness encoding follows look.maxThickness, rewrite texels after changing it
FilmDecal opacity(float v);
boolean isEmpty();
void remove(); // gl side freed on the render thread
```

`FilmLook` setters: `absorption`, `agedAbsorption`, `stainTint`, `scatter`, `maxThickness`, `edge(thickness, softness)`, `edgeNoise(amount, scale)`, `stainThreshold`, `ring`, `normals(strength, rim)`, `flow(speed, ripple)`, `parallax`, `specular`, `roughness(wet, dry)`, `reflectance`, `pixelArt(boolean)`, `texelsPerUnit(float)`, `pixelStyle(jitter, outline, core, highlights)`, `pattern(wet, dry)`, `palette(int...)`, `copy()`.

---

## Ordering and blending

Decals run in the `GEOMETRY` stage of Amnetic's [render pipeline](Render-Pipeline) at order 50, after the gbuffer normal fill, instanced meshes, and models, and before the shadow and deferred lighting passes. That placement is what makes decals participate in lighting: their color lands in the frame before the light pass captures it, and their gbuffer writes land before lighting reads normals and materials.

Because the whole `GEOMETRY` stage runs under vanilla translucents, water and glass correctly draw over decals.

Within the pass, decals draw in creation order with standard alpha blending (`SRC_ALPHA, ONE_MINUS_SRC_ALPHA`, destination alpha preserved). Overlapping decals stack painter-style: the most recently created decal composites on top. There is no z-ordering or priority field; if you need a specific stack, create the decals in that order (the editor's Duplicate button, for instance, puts the copy on top).

---

## Where it sits in the frame

```
GEOMETRY   gbuffer fill -> instanced meshes -> models -> decals (50) -> film decals (52)   <- here
LIGHTING   shadow map -> deferred lighting
SCREEN_SPACE, AFTER_WATER, ATMOSPHERE, POST ...
```

The pass early-outs when there are no decals, so an empty list costs nothing. Depth is copied from the main target once per frame that decals render. Each decal is one fullscreen triangle (two for relightable decals), scissored to the screen rect of its projection box, and decals whose box is entirely outside the view frustum are skipped. Cost therefore scales with the screen area each decal actually covers, plus a small per-decal overhead. When the camera is inside a box, the scissor falls back to the full screen.

---

## Editor

You can place and tune decals live. Open the in-game editor and navigate to Renderer then Decals. You get spawn controls (texture picker, size, and an optional relightable block with normal map, roughness, and metallic), and a live list of every active decal with position, normal, size, opacity, angle fade, tint, and the relight material, plus Duplicate and Remove.

The viewport draws a wireframe gizmo box around every decal showing its exact projection volume, with a center dot you can click to select the decal (the selected one highlights and its inspector entry opens). Toggle the gizmos with the "Show decal gizmos" checkbox in the panel.

---

## Limitations

* Every decal still costs a draw call and a state change, so hundreds of decals cost more than their pixels alone. A camera inside a decal box shades the full screen for that decal.
* Decals only appear on geometry that wrote depth into the main target before the `GEOMETRY` stage: terrain, entities, and vanilla-rendered content. Amnetic instanced meshes and models drawn in the same stage are covered too, but anything drawn after the decal pass (translucents, `AFTER_WATER` geometry) is not.
* Surface normals come from depth reconstruction, so the angle fade uses geometric normals only; it doesn't know about normal-mapped surface detail underneath.
* No rotation control in the decal plane. The texture's orientation is fixed by the derived right/up basis; for a floor decal, rotating the artwork means rotating the texture itself.
* No z-order or priority between overlapping decals beyond creation order, and no per-decal enable toggle. Remove and recreate.
* Relightable decals need the deferred pipeline's gbuffer populated that frame; without it they degrade to the plain color path.

---

## Reference

### `Decals`

```java
static Decal box(Identifier texture, Vec3 center, Vector3f normal,
                 float width, float height, float depth); // create + register, live immediately
static List<Decal> active(); // the live list (iteration-safe)
static boolean hasRelightable(); // any active decal writing the gbuffer?
static void clear(); // remove every decal
static void render(); // driven by Amnetic's pipeline; don't call yourself
static void dispose(); // frees GPU resources; called on client shutdown
```

### `Decal` (all setters chain, all live)

```java
Decal center(Vec3 c); // box center, world blocks
Decal normal(float x, float y, float z); // facing / projection axis. normalized; zero ignored
Decal width(float v); // full extent on the right axis, >= 0
Decal height(float v); // full extent on the up axis, >= 0
Decal depth(float v); // projection thickness along the normal, >= 0
Decal opacity(float v); // alpha multiplier, clamped 0..1. default 1
Decal angleFade(float v); // alignment cutoff, clamped 0..1. default 0.3
Decal tint(float r, float g, float b); // multiplied into texture RGB. default 1,1,1
Decal multiply(boolean v); // multiply onto the surface instead of alpha blending. default false
Decal flipV(boolean v); // image top row at the top of the box, wall sprites need it. default false
Decal texture(Identifier id); // swap the texture, e.g. for animation frames

// relightable (gbuffer) path
Decal normalMap(Identifier id); // tangent-space normal map; null clears it
Decal roughness(float v); // material override; < 0 = off (the default, -1)
Decal metallic(float v); // clamped 0..1. default 0. relightable path only

boolean writesGBuffer(); // normalMap set or roughness >= 0
float roughnessOr(float fallback); // roughness, or fallback when not overridden

void remove(); // permanent
boolean isRemoved();

// read-only accessors
Identifier texture(); Vec3 center(); Vector3f normal();
float width(); float height(); float depth();
float opacity(); float angleFade(); Vector3f tint();
Identifier normalMap(); float metallic();
```

---

## See Also

- [Deferred Lights](Deferred-Lights). The lighting pass that lights plain decals as albedo and relights gbuffer decals as their own material.
- [Render Pipeline](Render-Pipeline). Where the decal pass slots in, and how to reorder or replace it.
- [Framebuffers](Framebuffers). The depth capture and reconstruction machinery the pass is built on.
