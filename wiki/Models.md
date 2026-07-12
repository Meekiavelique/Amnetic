# Models

Amnetic loads glTF models and draws them in the world with a full PBR shader: metallic/roughness Cook-Torrance shading, normal maps, image-based lighting from a baked environment probe, skeletal animation with GPU skinning, shadow casting, and gbuffer output so [deferred lights](Deferred-Lights), [SSAO/SSGI](Screen-Space-Effects), and [Bloom](Bloom) all see the model like any other geometry.

The shortest path from a `.glb` file to a model standing in the world:

```java
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.Models;
import com.meekdev.amnetic.client.model.WorldModels;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

Model model = Models.load(Identifier.fromNamespaceAndPath("example", "models3d/refrigerator.glb"));

WorldModels.place(model, new BlockPos(100, 64, 200))
        .yaw(90f)
        .scale(1.5f)
        .playFirst(); // loop the first animation clip, if the file has one
```

`Models.load` returns immediately. If the model hasn't been converted yet it comes back as a not-ready `Model` that the renderer silently skips; it pops in a frame or two after the background conversion and GPU upload finish. Check `model.isReady()` if you need to know.

---

## Loading

Two formats load: **glTF** (`.gltf` and `.glb`) and Amnetic's own binary **`.ammesh`**. OBJ support was removed; `Models.load` throws a `ModelLoadException` telling you to convert to `.ammesh` if it sees one.

### The async glTF path

glTF is never parsed on the calling thread. `Models.load(Identifier)` for a `.gltf`/`.glb` resource works through a disk cache of pre-converted `.ammesh` binaries:

1. On a cache hit, the cached bytes are read and the model is queued for GPU upload on the render thread.
2. On a miss, a pending (not-ready) `Model` is returned and a background thread converts the glTF to `.ammesh`, stores it, and completes the load.

The cache lives at `<gameDir>/amnetic/ammesh_cache/` with a `manifest.json` keyed on the source bytes, so it survives restarts and invalidates itself when the source model changes. At startup Amnetic also scans every resource under `models/` ending in `.gltf`/`.glb` and converts them ahead of time on a background thread, so by the time your code calls `Models.load` it is usually a cheap binary read.

Either way, the actual GPU upload only happens on the render thread, and at most one not-yet-uploaded model uploads its geometry per frame. A scene with many heavy models streams in over a few frames instead of hitching on one.

`Models.load(byte[], ModelFormat)` exists for bytes you already have in memory, but only `AMMESH` bytes work there; raw glTF bytes throw, because they would have to parse synchronously.

### Draw submission

A `Model` doesn't draw by itself; you submit draws each frame. `Models.onFrame` registers a per-frame callback that runs during Amnetic's model pass with an `InstanceRenderContext` (client, world, camera position, view/projection matrices, delta tick):

```java
Models.onFrame(ctx -> {
    model.render(new Vec3(100, 64, 200), yawDegrees, 1f); // pos, yaw, scale
});
```

Draws are camera-relative internally, so positions far from the origin don't shimmer. Each submitted draw is frustum-culled against the model's bounding box (see `ModelConfig.frustumCull`), and the model samples the block/sky light level at its world position so it tracks day/night and dark interiors like vanilla geometry. `WorldModels`, `ModelBlockEntity`, and `ModelInstance` (below) wrap this loop for the common cases.

---

## Materials

Every material from the source file is exposed as a `ModelMaterial`. Get one by name with `model.material("Body")` or iterate `model.materials()`. All setters chain and take effect on the next frame's draw.

The surface is a standard metallic/roughness PBR material:

- `setBaseColor(r, g, b[, a])` tints (multiplies) the albedo. If the material has a base color texture, the two combine.
- `setMetallic(f)` and `setRoughness(f)` (both clamped 0..1) drive the Cook-Torrance specular lobe and the environment reflection blur. These are fully live: a metal at low roughness gets sharp sun highlights and glossy sky reflections.
- `setEmissive(r, g, b)` sets the emissive color. Emissive output still glows in the dark and is written to the gbuffer emissive target, so [Bloom](Bloom) in gbuffer mode picks it up.
- `setOpacity(a)` sets base alpha; any value below `0.999` also flips the material to blended rendering.
- `setAlphaCutoff(f)` enables alpha-test discard at the given threshold (on top of a hard floor: fragments below alpha `0.003` always discard).
- `setBlend(bool)` forces blended rendering on or off explicitly.
- `setTransmission(f)` sets the KHR transmission factor and switches the material onto a glass path: crisp environment reflection plus a faint tint, where the roughness value doubles as a dirt/frost mask (rough "glass" reads as frosted). `0` disables it.
- `setDoubleSided(bool)` disables backface culling for this material.
- `setShadingModel(ShadingModel)` tags the material's gbuffer ID so the deferred pass shades it with a custom response; see [Shading-Models](Shading-Models).

Textures can be swapped at runtime; each setter clears any embedded image bytes from the source file:

- `setBaseColorTexture(Identifier)` / `clearBaseColorTexture()`
- `setNormalTexture(Identifier)` for tangent-space normal maps
- `setMetallicRoughnessTexture(Identifier)` for an ORM-packed map (R occlusion, G roughness, B metallic). Because plain glTF metallicRoughness textures leave R at zero, the shader remaps R into `[0.35, 1]` instead of treating it as full occlusion; real packed AO still darkens, a bare MR texture doesn't black out.
- `setEmissiveTexture(Identifier)`
- `setEmissiveGlTexture(int glId)` binds a caller-owned live GL texture as the emissive map (e.g. a video screen you update with `glTexSubImage2D`). You keep ownership; pass `0` to detach.

```java
model.material("Screen")
        .setEmissive(1f, 1f, 1f)
        .setEmissiveTexture(Identifier.fromNamespaceAndPath("example", "textures/screen_on.png"));

model.material("Glass")
        .setTransmission(0.9f)
        .setRoughness(0.05f); // clean glass; higher roughness reads as frost
```

---

## Animation and skinning

Animation is real skeletal animation with GPU skinning, not a whole-model transform. The `Animator` samples the clip's translation/rotation/scale channels (linear or step interpolation, slerp for rotations), builds the node hierarchy's world-pose matrices on the CPU, and the vertex shader skins vertices from their joint indices and weights.

```java
Animator anim = model.createAnimator();
anim.play("walk").loop(true).speed(1.2f);

Models.onFrame(ctx -> {
    anim.update(ctx.deltaTick() / 20f); // dt in seconds
    model.renderPosed(worldMatrix, anim.pose());
});
```

- `play(clip)` starts a clip from time zero. `model.firstClip()` gives you the first clip's name, `model.isAnimated()` tells you the file has any.
- `loop(bool)` loops (default) or clamps at the clip end. `speed(f)` is a playback multiplier. `setTime(seconds)` scrubs.
- `crossfade(from, to, seconds)` plays `from` while blending toward `to` over the given duration, then switches to `to`.
- `update(dt)` advances the clock; `pose()` computes and returns the current world-pose matrix array to pass to `renderPosed`.

`ModelInstance` bundles a model, an animator, and a transform when you don't want to manage them separately:

```java
ModelInstance instance = new ModelInstance(model);
instance.animator().play("idle");

Models.onFrame(ctx -> {
    instance.setTransform(new Matrix4f().translation(x, y, z))
            .update(ctx.deltaTick() / 20f)
            .render(); // renderPosed if animated, plain render otherwise
});
```

`WorldModels` placements handle animation too: `placement.play("walk")` or `placement.playFirst()` creates and updates an animator for you.

---

## Level of detail

LOD is opt-in per model with `model.config().lod(true)`. Once enabled, a low-priority background thread runs quadric-error-metric (QEM) simplification over each qualifying part and publishes a ladder of coarser index lists; rendering never stalls waiting for it.

- Four levels: full detail plus simplified levels at roughly 45%, 18%, and 6% of the original triangle count.
- The renderer picks the level by the nearest instance's camera distance: level 0 under 22 blocks, level 1 under 45, level 2 under 80, level 3 beyond.
- Parts with skinning, without indices, or under 1500 triangles are left at full detail. A failed simplification also just leaves that part at full detail.

Enable it on heavy props; leave small or flat geometry alone.

---

## Lighting

Models are forward-shaded by the built-in PBR shader, then participate in the deferred pipeline through their gbuffer writes. Per fragment the shader computes:

- **Direct sun**: a Cook-Torrance specular lobe plus Lambertian diffuse against a configurable sun direction and color, scaled by the local sky light.
- **Image-based ambient**: irradiance and glossy reflections sampled from a prefiltered environment cubemap baked from the sky (it re-bakes cheaply as the sun moves, so reflections follow time of day). If the probe isn't ready yet, an analytic sky gradient stands in. Roughness selects the reflection mip, so rough surfaces get blurry reflections and polished ones stay sharp.
- **Lightmap scaling**: the result is multiplied by the block/sky light sampled at the instance position (floored so nothing goes fully black), and emissive is added on top so it glows in the dark.
- **ACES filmic tonemapping** (toggleable) and an exposure multiplier, then a linear-to-sRGB conversion.

It also writes the gbuffer normal, material (roughness, metallic, shading-model ID), and emissive targets, which is what lets shadows, deferred lights, SSR, and bloom treat models as first-class geometry.

The global knobs live on `ModelLighting.INSTANCE` and chain:

```java
import com.meekdev.amnetic.client.model.ModelLighting;

ModelLighting.INSTANCE
        .sunDirection(0.35f, 0.85f, 0.40f)
        .sunColor(1.0f, 0.97f, 0.92f)
        .sunIntensity(1.2f)
        .exposure(1.1f);
```

| Setter | Default | Effect |
| --- | --- | --- |
| `sunDirection(x, y, z)` | `0.35, 0.85, 0.40` | direction toward the sun |
| `sunColor(r, g, b)` | `1.0, 0.97, 0.92` | sun tint |
| `sunIntensity(f)` | `1.0` | direct sun multiplier, clamped >= 0 |
| `ambientStrength(f)` | `1.0` | scales IBL irradiance and reflections, >= 0 |
| `envIntensity(f)` | `1.0` | environment probe multiplier, >= 0 |
| `exposure(f)` | `1.0` | final exposure, clamped >= 0.01 |
| `tonemap(bool)` | `true` | ACES filmic tonemap on/off |

Models also cast into the [Shadows](Shadows) bake by default; turn it off per model with `model.config().castsShadow(false)`.

---

## Placing models in the world

For a model that just stands somewhere, `WorldModels` runs the per-frame submission for you:

```java
WorldModels.Placement placement = WorldModels.place(model, pos) // BlockPos, centered on the block
        .offset(0, 0.5, 0) // fine-tune in blocks
        .yaw(45f)
        .scale(2f)
        .playFirst();

placement.remove(); // stop drawing it
WorldModels.clear(); // drop every placement
```

To tie a model to a block, extend `ModelBlockEntity`. It creates a placement when the block entity enters a client level and removes it when the block is gone; override `configurePlacement` to set yaw, scale, or animation from the block state:

```java
public class FridgeBlockEntity extends ModelBlockEntity {
    private static final Identifier MODEL =
            Identifier.fromNamespaceAndPath("example", "models3d/refrigerator.glb");

    public FridgeBlockEntity(BlockPos pos, BlockState state) {
        super(FridgeDemo.BLOCK_ENTITY, pos, state, () -> Models.load(MODEL));
    }

    @Override
    protected void configurePlacement(WorldModels.Placement placement, BlockState state) {
        placement.playFirst();
    }
}
```

For anything dynamic (entities, projectiles, moving props), drive `Model.render` yourself from `Models.onFrame`:

```java
model.render(worldPos, yawDegrees, scale); // convenience overloads
model.render(worldPos, quaternion, scale);
model.render(matrix); // arbitrary Matrix4f world transform
model.renderInstanced(m1, m2, m3); // several transforms in one call
```

`model.fillMask(projView, world, pose, r, g, b, a)` draws the model's geometry as a flat solid color with a minimal shader; useful for stencil-style masks and outlines.

---

## View space and offscreen

### `HandModels`: replace a held item's first-person model

Bind a `Model` to an `Item` and Amnetic swaps it in for the vanilla first-person held-item render, tracking the vanilla hand pose (sway, swing, equip animation):

```java
HandModels.bind(MY_ITEM, () -> Models.load(MODEL_ID))
        .fit(0.3f) // scale so the bounding radius is 0.3
        .offset(0f, -0.05f, 0f)
        .rotation(0f, 90f, 0f)
        .light(1f, 0f) // block, sky
        .emissive(1.5f);
```

`fit(radius)` auto-scales from the model's bounds and recenters it; `scale(f)` is the manual alternative. By default the depth buffer is cleared before the draw so the model never clips into walls (same as the vanilla hand); `overlayOnWorld()` skips the clear so world geometry occludes it. `unbind(item)` removes the binding. Only the local player's first-person view is affected. (`beginFrame`, `captureProjection`, `captureHeld`, and `render` are engine hooks driven by mixins; you only call `bind`/`unbind`.)

### `ViewModels`: persistent view-space geometry

`ViewModels` draws models in first-person view space independent of what's held: first-person arms, a persistent weapon, a tool. Handles draw at the vanilla first-person FOV with the camera at origin looking down -Z, so `offset(x, y, z)` is camera space (x right, y up, +z forward):

```java
ViewModels.Handle handle = ViewModels.add(Identifier.fromNamespaceAndPath("example", "models3d/arm.glb"))
        .fit(0.4f)
        .offset(0.3f, -0.25f, 0.5f)
        .rotation(0f, -10f, 0f)
        .depthRange(0.0, 0.1); // self-occludes, stays in front of the world, no depth clear

handle.visible(false); // toggle without removing
handle.transform(cameraSpaceMatrix); // or take over the full transform; null reverts
ViewModels.remove(handle);
```

Three depth strategies per handle: `depthClear()` (default; never clips the world but pops through walls), `depthRange(near, far)` (squeezes the model into a front slice of the depth range, keeping self-occlusion without a clear), and `overlayOnWorld()` (draws against world depth so walls occlude it).

### `ModelView` and `SceneView`: render to a texture

`ModelView` renders a single model to an offscreen framebuffer with an auto-framing orbit camera; draw the result anywhere a GUI texture goes:

```java
ModelView view = new ModelView(256, 256)
        .model(model)
        .yaw(30f).pitch(20f).zoom(1.2f)
        .play("idle");

// each frame (e.g. in a screen's render method)
view.spin(45f, dt).update(dt).render()
        .register(Identifier.fromNamespaceAndPath("example", "preview")); // expose as a GUI texture
int glId = view.textureId(); // or use the raw GL texture id

view.dispose(); // frees the framebuffer and renderer
```

`SceneView` is the multi-model sibling with a free camera, for menu backdrops and dioramas: `add(model, worldMatrix)` entries (the matrix is referenced, not copied, so mutate it to animate), then `camera(...)`, `fov(...)`, `clip(...)`, `clearColor(...)`, `light(...)`, and `render()` each frame. Entries whose model is still loading are skipped and fill in as async loads finish.

Both views are forward-lit by the stock model shader only; no deferred lights, shadows, or post inside the offscreen render.

### `ItemModels`: a live 3D render as an item texture

`ItemModels.bind(textureId, view)` registers a `ModelView`'s output under a texture `Identifier` every frame, so an item model (or anything else) referencing that texture shows a live, spinning 3D render:

```java
ItemModels.Binding binding = ItemModels.bind(
        Identifier.fromNamespaceAndPath("example", "item/sword_live"),
        new ModelView(128, 128).model(model))
        .spin(60f); // degrees per second; .still() to freeze

binding.remove(); // unhooks and disposes the view
```

---

## Procedural meshes

`Meshes` builds a `Model` from raw vertex arrays for geometry that never comes from a file:

```java
import com.meekdev.amnetic.client.model.Meshes;

Model tri = Meshes.build(new float[] {
        0f, 0f, 0f,
        1f, 0f, 0f,
        0f, 1f, 0f,
});
```

`build(positions)` takes packed xyz floats and fills in the rest: smooth normals computed from the triangles (area-weighted), zero UVs, sequential indices. `build(positions, normals, uvs, indices)` lets you supply any subset (pass `null` for the defaults), and a third overload adds a caller-supplied material. The result is a normal `Model`: give it materials via `materials()`, a config, and render it like anything else.

---

## Configuration

Each model owns a `ModelConfig`, reachable as `model.config()`. All setters chain.

| Setter | Default | Effect |
| --- | --- | --- |
| `renderState(RenderState)` | `RenderState.DEFAULT` | GL state (blend, cull, depth) applied around the draw |
| `writeGBuffer(bool)` | `true` | write normal/material/emissive gbuffer targets |
| `emissive()` / `emissive(strength)` | off | whole-model emissive strength multiplier |
| `castsShadow(bool)` | `true` | include in the [Shadows](Shadows) bake |
| `frustumCull(bool)` | `true` | per-draw AABB culling; turn off for tiled geometry like streamed terrain chunks where per-chunk tests can leave holes |
| `lod(bool)` | `false` | opt-in QEM distance LOD, generated off-thread |
| `shaders(vsh, fsh)` / `shader(id)` | built-in | custom GLSL program |

### Custom shaders

`shaders(vsh, fsh)` swaps the built-in program for your own. Identifiers are raw and resolve like the instancing shaders: `Identifier.fromNamespaceAndPath("stun", "model_snap")` resolves to `stun:shaders/model_snap.vsh` and `.fsh`. `shader(id)` is shorthand when both stages share a base id.

Your program gets the standard attribute layout (0 Position, 1 Normal, 2 UV, 3-6 instance matrix, 7 InstLight, 8 Tangent, 9 Joints, 10 Weights) and whatever uniforms it declares from the engine's set (ProjViewMatrix, Time, the lighting and material uniforms, the samplers including the environment cubemap, EmissiveStrength, skinning). The fragment stage writes the gbuffer targets like the built-in shader. Programs compile once and are cached per vsh/fsh pair; a failed compile logs, falls back to the default shader, and caches the fallback so it doesn't retry every frame. In dev, shader hot reload rebuilds them.

---

## Disposal and lifecycle

`model.dispose()` releases the model through a ref-counted registry; when the count hits zero the model is unregistered and its GPU buffers are freed. After disposal, pending and future `render` calls on that handle are ignored. Don't dispose a `Model` that other code (a `WorldModels` placement, a `ViewModels` handle, another instance) is still drawing.

`ModelInstance.dispose()` only disposes its animator; the underlying `Model` stays alive, so several instances can share one model safely and you dispose the model separately when nothing uses it anymore.

The offscreen views own GPU resources of their own: call `dispose()` on `ModelView`/`SceneView` you created (or `remove()` on an `ItemModels.Binding`, which disposes its view). `WorldModels.clear()`, `ViewModels.clear()`, and `ItemModels.clear()` drop everything registered with each system.

On client shutdown Amnetic disposes every registered model, shader, and view-model renderer itself; session cleanup is on you, exit cleanup is not.

---

## Limitations

- **glTF and `.ammesh` only.** OBJ was removed; convert to `.ammesh`. Raw glTF bytes can't be loaded through `Models.load(byte[], ...)` either, only resource-backed identifiers (or pre-converted `.ammesh` bytes).
- **Loading is asynchronous.** A first-ever load of a big glTF takes as long as the background conversion takes; the model is invisible until then. `isReady()` is your only signal; there is no completion callback on `Model`.
- **Each `Models.load(Identifier)` call creates its own `Model`** with its own GPU copy, even for the same file. Load once and share the handle if you need many instances of the same asset; use `render`/`renderInstanced` with multiple transforms for true instancing.
- **Bounds are zero until loaded.** `center()`, `radius()`, `boundsMin()`/`boundsMax()` return zeros while a model is still pending, so anything derived from them (like a `fit()` before ready) settles once the load completes.
- **Offscreen views are forward-only.** `ModelView` and `SceneView` render with the stock model shader; no deferred lights, shadows, SSAO, or post inside the texture.
- **LOD skips skinned and small parts.** Skinned meshes, unindexed parts, and parts under 1500 triangles always draw at full detail.
- **Instance light is sampled at one point.** The block/sky light comes from the transform's translation, so a very large model spanning light and dark areas is lit uniformly from that one sample.
- **`HandModels` is local-player, first-person only.** Third-person and other players keep the vanilla item model.
- **Uploads are budgeted.** One first-time GPU upload per frame; a burst of new models pops in over several frames by design.

---

## Reference

### `Models`

```java
static Model load(Identifier id); // .gltf/.glb (async via .ammesh cache) or .ammesh
static Model load(byte[] bytes, ModelFormat format); // AMMESH only; GLTF/OBJ throw
static Model register(Model model); // register a hand-built Model
static Model fromIR(ModelIR ir); // wrap an intermediate representation
static void onFrame(Consumer<InstanceRenderContext> callback); // per-frame draw-submission hook
```

### `Model`

```java
boolean isReady(); // async load finished and GPU data live
String name();
ModelConfig config();
boolean isAnimated();
String firstClip(); // first animation clip name, or null

Model render(Matrix4fc worldTransform);
Model render(Vec3 worldPos, float scale);
Model render(Vec3 worldPos, float yawDegrees, float scale);
Model render(Vec3 worldPos, Quaternionfc rotation, float scale);
Model renderInstanced(Matrix4fc... worldTransforms);
Model renderPosed(Matrix4fc worldTransform, Matrix4f[] pose); // skinned draw with an Animator pose

ModelMaterial material(String name); // null if absent
List<ModelMaterial> materials();
Animator createAnimator();

Vector3f center();   float radius(); // bounding sphere (zeros until loaded)
Vector3f boundsMin();   Vector3f boundsMax(); // AABB

void fillMask(Matrix4fc projView, Matrix4fc world, Matrix4f[] pose,
              float r, float g, float b, float a); // flat solid-color draw

void dispose(); // ref-counted release; frees GPU data at zero
```

### `ModelInstance`

```java
ModelInstance(Model model); // creates its own Animator
Model model();   Animator animator();
ModelInstance setTransform(Matrix4fc t);   Matrix4f transform();
ModelInstance update(float dt); // advances the animator, dt seconds
ModelInstance render(); // renderPosed when animated, plain render otherwise
void dispose(); // disposes the animator only, not the model
```

### `Animator`

```java
Animator play(String clip); // restart from t = 0
Animator loop(boolean loop); // default true
Animator speed(float speed); // default 1
Animator setTime(float seconds);
Animator crossfade(String from, String clip, float seconds);
Animator update(float dt); // dt seconds
Matrix4f[] pose(); // sample channels, return world-pose matrices for renderPosed
Model model();
String current();   boolean isLooping();   float speedValue();   float time();
void dispose();
```

### `ModelMaterial` (all setters chain)

```java
String name();
ModelMaterial setBaseColor(float r, float g, float b);
ModelMaterial setBaseColor(float r, float g, float b, float a);
ModelMaterial setOpacity(float a); // < 0.999 also enables blending
ModelMaterial setMetallic(float f); // clamped 0..1
ModelMaterial setRoughness(float f); // clamped 0..1
ModelMaterial setEmissive(float r, float g, float b);
ModelMaterial setAlphaCutoff(float cutoff); // clamped 0..1; 0 = off (hard 0.003 floor always applies)
ModelMaterial setBlend(boolean blend);
ModelMaterial setTransmission(float f); // KHR transmission; 0 disables the glass path
ModelMaterial setDoubleSided(boolean doubleSided);
ModelMaterial setShadingModel(ShadingModel model); // see Shading-Models
ModelMaterial setBaseColorTexture(Identifier texture);   ModelMaterial clearBaseColorTexture();
ModelMaterial setNormalTexture(Identifier texture);
ModelMaterial setMetallicRoughnessTexture(Identifier texture); // ORM packing
ModelMaterial setEmissiveTexture(Identifier texture);
ModelMaterial setEmissiveGlTexture(int glId); // caller-owned live GL texture; 0 detaches

boolean hasBaseColorTexture();   int shadingModelId();
float baseR(); float baseG(); float baseB(); float baseA();
float metallic(); float roughness();
```

### `ModelConfig` (all setters chain)

```java
ModelConfig renderState(RenderState state); // default RenderState.DEFAULT
ModelConfig writeGBuffer(boolean value); // default true
ModelConfig emissive();   ModelConfig emissive(float strength); // default off
ModelConfig castsShadow(boolean value); // default true
ModelConfig frustumCull(boolean value); // default true
ModelConfig lod(boolean value); // default false; opt-in QEM distance LOD
ModelConfig shaders(Identifier vsh, Identifier fsh); // custom program, raw ids
ModelConfig shader(Identifier id); // shorthand: same base id for both stages
ModelConfig copy();

RenderState renderState(); boolean writeGBuffer(); boolean castsShadow();
boolean frustumCull(); boolean isLod(); boolean isEmissive(); float emissiveStrength();
boolean hasCustomShader(); Identifier customVsh(); Identifier customFsh();
```

### `ModelLighting` (singleton, all setters chain)

```java
ModelLighting.INSTANCE
ModelLighting sunDirection(float x, float y, float z); // default 0.35, 0.85, 0.40
ModelLighting sunColor(float r, float g, float b); // default 1.0, 0.97, 0.92
ModelLighting sunIntensity(float v); // >= 0, default 1
ModelLighting ambientStrength(float v); // >= 0, default 1
ModelLighting envIntensity(float v); // >= 0, default 1
ModelLighting exposure(float v); // >= 0.01, default 1
ModelLighting tonemap(boolean v); // default true (ACES)
```

### `Meshes`

```java
static Model build(float[] positions); // xyz packed; smooth normals, zero UVs, sequential indices
static Model build(float[] positions, float[] normals, float[] uvs, int[] indices); // nulls = defaults
static Model build(float[] positions, float[] normals, float[] uvs, int[] indices,
                   ModelIR.Material material);
```

### `WorldModels` / `Placement`

```java
static Placement place(Model model, BlockPos pos); // centered on the block, at its base
static void clear();

Placement play(String clip);   Placement playFirst(); // looped
Placement yaw(float degrees);   Placement scale(float scale);
Placement offset(double dx, double dy, double dz);
void remove();
```

### `ModelBlockEntity`

```java
ModelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                 Supplier<Model> modelSupplier);
protected void configurePlacement(WorldModels.Placement placement, BlockState state); // override
```

### `HandModels` / `Binding`

```java
static Binding bind(Item item, Model model);
static Binding bind(Item item, Supplier<Model> modelSupplier);
static void unbind(Item item);

Binding scale(float scale); // default 0.4
Binding fit(float targetRadius); // auto-scale from bounds, recentered
Binding offset(float x, float y, float z);
Binding rotation(float pitchDeg, float yawDeg, float rollDeg);
Binding light(float block, float sky); // default 1, 0
Binding emissive(float strength); // default 1
Binding overlayOnWorld(); // skip the depth clear; world occludes the model
```

### `ViewModels` / `Handle`

```java
static Handle add(Model model);
static Handle add(Supplier<Model> modelSupplier);
static Handle add(Identifier modelId); // lazy Models.load
static void remove(Handle handle);   static void clear();

Handle visible(boolean visible);   boolean isVisible();
Handle scale(float scale);   Handle fit(float targetRadius);
Handle offset(float x, float y, float z); // camera space: x right, y up, +z forward
Handle rotation(float pitchDeg, float yawDeg, float rollDeg);
Handle light(float block, float sky);   Handle emissive(float strength);
Handle transform(Matrix4fc cameraSpace); // full override; null reverts to the builder fields
Handle depthClear(); // default: clear depth before drawing
Handle depthRange(double near, double far); // front depth slice, keeps self-occlusion
Handle overlayOnWorld(); // draw against world depth
```

### `ModelView`

```java
ModelView(int width, int height);
ModelView model(Model model);
ModelView yaw(float degrees);   ModelView pitch(float degrees);
ModelView fov(float degrees);   ModelView zoom(float zoom);
ModelView light(float block, float sky);   ModelView emissive(float strength);
ModelView play(String clip);   ModelView update(float dt);
ModelView spin(float degreesPerSecond, float dt);
ModelView render(); // no-op until the model is ready
int textureId();   ModelView register(Identifier id); // expose as a GUI texture, after render()
int viewWidth();   int viewHeight();
void dispose();
```

### `SceneView`

```java
SceneView(int width, int height);
SceneView add(Model model, Matrix4f world); // matrix referenced, not copied
SceneView clearModels();
SceneView camera(float eyeX, float eyeY, float eyeZ, float lookX, float lookY, float lookZ);
SceneView fov(float degrees);   SceneView clip(float near, float far);
SceneView clearColor(float r, float g, float b);
SceneView light(float block, float sky);
SceneView render(); // skips entries whose model isn't ready yet
int textureId();   SceneView register(Identifier id);
int viewWidth();   int viewHeight();
void dispose();
```

### `ItemModels` / `Binding`

```java
static Binding bind(Identifier textureId, ModelView view); // re-registers the view's texture each frame
static void clear();

Binding spin(float degreesPerSecond); // default 45
Binding still();
ModelView view();
void remove(); // unhooks and disposes the view
```

### `ModelFormat`

```java
GLTF // .gltf / .glb -- loads via the async .ammesh cache
OBJ // detected but no longer parseable; convert to .ammesh
AMMESH // Amnetic binary, magic "AMSH"

static ModelFormat fromPath(String path);
static ModelFormat fromIdentifier(Identifier id);
static ModelFormat fromMagic(byte[] bytes);
```

---

## See Also

- [Deferred-Lights](Deferred-Lights). Models write color and depth into the main target, so your custom lights hit them.
- [Shadows](Shadows). Models cast into the shadow bake by default; `castsShadow(false)` opts out.
- [Shading-Models](Shading-Models). Per-material shading responses via `setShadingModel`.
- [Bloom](Bloom). Emissive materials and `ModelConfig.emissive` feed the gbuffer emissive target.
- [Instanced-Rendering](Instanced-Rendering). The lower-level instancing API models are built on.
- [Writing-Shaders](Writing-Shaders). Authoring the custom programs `ModelConfig.shaders` takes.
