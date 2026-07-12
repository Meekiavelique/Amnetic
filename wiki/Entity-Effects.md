# Entity Effects

Run a custom shader over any living entity. You provide the vertex and fragment shaders. The renderer pushes the entity geometry through them while Amnetic handles the uniforms and textures. If you can write it in GLSL, you can put it on an entity.

We have two primitives for different jobs.

`EntityEffects.surface(...)` is the heavy lifter. It completely replaces the entity's render type with your custom shaders. You own the look completely.

`EntityTextureOverride` is the cheap option. It keeps the vanilla shader and only swaps the texture. No GLSL required. Use it for disguises or damage states.


## The pipeline

Calling `EntityEffects.surface(entity, vsh, fsh, cfg)` registers the effect. The registry maps one effect per entity. Registering a new one overwrites the old.

Every frame, Amnetic stamps your effect onto the entity's render state. When the engine asks how to draw the body, Amnetic intercepts the call. It returns a render type built from your shaders instead of the vanilla material. The library binds your required samplers and UBOs, uploads your uniforms, and pushes the quads through your pipeline.

You never touch the render loop. You write two GLSL files and configure the inputs.

### Surface vs Texture Override

| Feature | `EntityEffects.surface` | `EntityTextureOverride` |
|---|---|---|
| Shader | Yours (`vsh` and `fsh`) | Vanilla entity shader |
| Can read scene color and depth | Yes | No |
| Can animate per frame | Yes | No |
| Cost | Higher | Near zero |
| Best for | Custom materials | Skins and disguises |

## SurfaceConfig

```java
EntityEffect surface(LivingEntity entity, Identifier vsh, Identifier fsh,
                     Consumer<SurfaceConfig> configurator);
```

The `vsh` and `fsh` arguments are standard ResourceLocations resolving to your shader assets. Passing the same Identifier for both is normal practice. The loader appends `.vsh` and `.fsh` automatically. The configurator block locks in your pipeline settings instantly.

```java
SurfaceConfig skin(boolean enabled); // default false
SurfaceConfig sceneColor(boolean enabled); // default true
SurfaceConfig sceneDepth(boolean enabled); // default false
SurfaceConfig replaceBody(boolean replace); // default true; false disables the effect (see below)
SurfaceConfig uniform(int channel, float value01);
SurfaceConfig uniform(int channel, DoubleSupplier supplier);
SurfaceConfig sampler(String name, Identifier texture);
```

The `skin` toggle defaults to false. Turning it on binds the entity's base texture as `Sampler0`. The `UV0` attribute is always present in the vertex format either way; the toggle only controls whether the skin texture is bound for you to sample. Turn it on if you need to read the original skin colors. Leave it off for purely procedural effects like refracting glass.

The `sceneColor` toggle defaults to true. Amnetic grabs a snapshot of the opaque world right before drawing translucents and binds it as `SceneColorSampler`. This makes refraction and screen-space tricks possible. Note that the snapshot blit runs whenever any entity effect is registered, whatever this flag says; turning it off only skips binding the sampler to your shader. Declare the sampler only when the flag is on.

The `sceneDepth` toggle defaults to false. It binds the scene depth buffer as `DepthSampler`. Enable this for soft intersections or depth-aware refraction offsets.

The `replaceBody` toggle defaults to true, which suppresses the vanilla body render type and all its extra layers like armor or held items. You get a completely blank slate. Setting it to false leaves the vanilla render fully intact, and because the render path only consumes effects that replace the body, your surface shaders never run at all. In practice, leave it at true.

The `uniform` method sets your variables. Passing a float sets a static value. Passing a DoubleSupplier evaluates the uniform every single frame. Suppliers are perfect for time-based changes without holding onto the effect handle. Setting one type clears the other on that specific channel.

The `sampler` method binds extra textures like noise maps or gradient ramps. Give it a name and it appears in your fragment shader as a standard `sampler2D`.

## The shader contract

You must match these exact names. If you guess the input names, the pipeline will fail to bind them.

### Vertex inputs

The pipeline uses `DefaultVertexFormat.ENTITY` drawn as quads. Your vertex shader can declare `Position`, `Color`, `Normal`, and `UV0` (always in the format; only useful for sampling the skin when the skin toggle is on). You are responsible for view and clip space transforms.

```glsl
#moj_import <minecraft:dynamictransforms.glsl> // ModelViewMat
#moj_import <minecraft:projection.glsl> // ProjMat

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
}
```

### UBOs

Import the standard headers and the variables are yours to use.

`#moj_import <minecraft:projection.glsl>` gives you `ProjMat`.
`#moj_import <minecraft:dynamictransforms.glsl>` gives you `ModelViewMat`.
`#moj_import <minecraft:globals.glsl>` gives you `ScreenSize` and `GameTime`.

`GameTime` increments slowly. Multiply it by 1000.0 for visible shader movement.

### Samplers

`UniformSampler` is always bound. `SceneColorSampler`, `DepthSampler`, and `Sampler0` require their matching config flags to be enabled in Java. Declaring them without the flag breaks the bind.

Divide `gl_FragCoord.xy` by `ScreenSize` to read the screen space UV.

```glsl
vec2 screenUv = gl_FragCoord.xy / ScreenSize;
vec3 behind   = texture(SceneColorSampler, screenUv).rgb;
float rawDepth = texture(DepthSampler, screenUv).r;
```

Depth outputs as non-linear. The glass example has a `linearDepth` helper to convert it to real distances. You compare that real distance against your view space position to prevent glass from refracting objects sitting in front of the entity.

Write your final color to `out vec4 fragColor`. The pipeline uses translucent blending with depth writes on. Drop fragments you do not need with `discard`.

## Uniform channels

Amnetic packs your numbers into a 4x1 RGBA8 texture called `UniformSampler`. This gives you 16 total channels numbered 0 through 15. Channel 0 is texel 0 red. Channel 1 is texel 0 green. Channel 4 is texel 1 red.

Read them in the fragment shader with a raw texel fetch.

```glsl
vec4 t0 = texelFetch(UniformSampler, ivec2(0, 0), 0);
float channel5 = texelFetch(UniformSampler, ivec2(1, 0), 0).g;
```

Everything clamps to the 0.0 to 1.0 range before upload. It also gets quantized to 8 bits. Colors and alphas handle this fine. Other values need packing. If you need a scanline speed of 8, pass `speed / 8.0` in Java and multiply it by 8.0 inside the shader. Every bundled example uses this packing convention.

Always reserve one channel for fade opacity and default it to 1.0.

## The handle lifecycle

Calling `surface` returns an `EntityEffect`.

```java
EntityEffect setUniform(int channel, float value01);
EntityEffect setUniform(int channel, DoubleSupplier s);
EntityEffect fadeOutOver(int ticks, int fadeChannel);
boolean replacesBody();
boolean isRemoved();
void remove();
```

You can update uniforms on the fly or trigger an automatic removal using `fadeOutOver`. The fade method drives your chosen channel down to zero over the tick count and then deletes the effect entirely. Your shader must multiply its final alpha by this channel for the visual fade to work.

Cleanup happens automatically. If the entity dies, despawns, or unloads, the effect is disposed. Client shutdowns clear everything. You only need to call `remove()` for a manual early exit.

## EntityTextureOverride

The lightweight alternative. It works on living entities  - the swap hooks the living-entity renderer, so while the API accepts any `Entity`, non-living entities are unaffected.

```java
void set(Entity entity, Identifier texture);
void set(Entity entity, Identifier texture, boolean hideLayers);
void clear(Entity entity);
void clearAll();
```

Calling `set(entity, texture)` forces the cutout render type to use your texture instead of the skin. Lighting and overlays behave exactly as usual.

Passing true for `hideLayers` suppresses armor and held items. You get a completely clean disguise.

## The bundled examples

These live in `com.example.entityfx.ShowcaseEffects`. Press F7 in the dev environment to cycle them on your own player.

**Glass (`ShowcaseEffects.glass`)**
Refraction and fresnel reflection. Reads scene color and scene depth. It uses channel 7 for the fade alpha. It packs index of refraction into channel 4 by dividing the target value by 4.0 in Java.

**Hologram (`ShowcaseEffects.hologram`)**
Scanlines and flicker. Reads the skin and scene color. Channel 8 handles the fade. It uses the model space Y coordinate passed from the vertex shader to map the scanlines onto the figure.

**Recolor (`ShowcaseEffects.recolor`)**
The cheapest custom effect. It samples the skin luminance and blends between a flat color and shaded paint. It disables scene color since it never reads the background (the snapshot blit still runs while any effect is registered; the flag just skips the sampler binding).

## Example: pulsing energy shield

A fresnel rim that breathes over time using a dynamic uniform. One file pair and no scene color needed.

**shield.vsh**

```glsl
#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec3 Normal;

out vec3 vViewPos;
out vec3 vViewNormal;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    vViewPos = viewPos.xyz;
    vViewNormal = mat3(ModelViewMat) * Normal;
    gl_Position = ProjMat * viewPos;
}
```

**shield.fsh**

```glsl
#version 330
uniform sampler2D UniformSampler;

in vec3 vViewPos;
in vec3 vViewNormal;
out vec4 fragColor;

void main() {
    vec4 c0   = texelFetch(UniformSampler, ivec2(0, 0), 0);
    float fade = texelFetch(UniformSampler, ivec2(1, 0), 0).r;

    vec3 N = normalize(vViewNormal);
    vec3 V = normalize(-vViewPos);
    float fresnel = pow(1.0 - clamp(dot(N, V), 0.0, 1.0), 3.0);

    float intensity = mix(0.3, 1.0, c0.a);
    vec3 color = c0.rgb * fresnel * intensity * 1.6;
    float alpha = clamp(fresnel * intensity, 0.0, 1.0) * fade;
    fragColor = vec4(color, alpha);
}
```

**Java**

```java
Identifier shield = Identifier.fromNamespaceAndPath("mymod", "entityfx/shield");

EntityEffect fx = EntityEffects.surface(entity, shield, shield, cfg -> cfg
        .sceneColor(false)
        .uniform(0, 0.3f)
        .uniform(1, 0.7f)
        .uniform(2, 1.0f)
        .uniform(3, () -> 0.5 + 0.5 * Math.sin(System.nanoTime() / 4.0e8))
        .uniform(4, 1.0f));
```
