# Scene Capture

Scene capture renders the world a second time, from a camera that is not the player's, into an off-screen texture  - and it does it without ever disturbing the frame the player actually sees. That texture goes live the same frame, so a shader or an instanced surface can sample it immediately. It is the raw material for mirrors, portals, security monitors, scrying pools, crystal balls, and anything else that needs "the world, but from over there."

The pieces fit together like this.

- **`PerspectiveCapture`** is the generic capture. You give it a per-frame callback that aims a virtual camera, and it renders the world from there into its own off-screen target.

- **`PerspectiveView`** is the thing your callback fills in each frame: where the eye sits, which way it looks, what projection it uses, and an optional clip plane.

- **`CaptureContext`** is the read-only frame info your callback receives  - the main camera's eye, view rotation, projection, and partial ticks  - so you can compute the virtual view relative to the player.

- **`CaptureResult`** is what your callback returns: `RENDER` to capture this frame, or `SKIP` to leave last frame's texture untouched.

- **`CaptureSurface`** is the ready-made display: a quad (or any position-only mesh) placed in the world that samples a capture feed, with optional masking. It saves you writing the instanced mesh and shader yourself for monitors, portals, and screens.

- **`PlanarReflection`** is the high-level mirror built on top of all of the above. Hand it a plane and it does the reflection math (the mirrored eye, the mirrored view, the oblique near-plane clip), and it can build the reflective surface for you.

A few things to keep in mind, up front, because they shape everything else.

- **A capture is a full extra world render.** One capture roughly doubles your world-render cost; two captures triple it. This is the single most important fact about the system. You lower the price with `resolution()` below `1.0`, and you avoid paying it at all by returning `SKIP` when the result is not visible.

- **At most four captures run per frame.** If more than four want to `RENDER`, only the four nearest (smallest `distance()`) actually render. The rest keep last frame's texture.

- **The captured texture is live the same frame.** No copy-back, no one-frame lag. Register it under an `Identifier` and any shader sampler or instanced surface can read it that same frame.

---

## The mental model: a second camera, a redirected target

The trick that makes this safe is small and worth understanding, because it explains every quirk below.

Right before vanilla renders the level, Amnetic's capture pass kicks in. For each active capture it:

1. Saves the main camera's position, rotation, frustum, visible chunk lists, and the shared `cameraRenderState`.
2. Poses the camera at your virtual eye and rotation, builds a fresh frustum, and re-walks the chunk occlusion graph for that view.
3. Flips a `capturing` flag and calls vanilla's `renderLevel` again.

While `capturing` is true, a mixin on `Minecraft.getMainRenderTarget()` quietly returns the capture's off-screen target instead of the real screen target. So vanilla renders the entire world  - terrain, entities, sky, the lot  - straight into the capture texture, thinking it is drawing to the screen. The player's view is never touched.

When the capture finishes, everything is restored: camera, frustum, chunk lists, render state, and the shared level extraction is refilled for the real view. Then vanilla proceeds with the normal frame as if nothing happened.

Because the capture reuses the genuine `renderLevel`, the off-screen image is the real game  - real lighting, real shaders, real entities  - not a cheap approximation. That fidelity is exactly why it costs a full render. A few things are deliberately suppressed during capture so they don't leak in: the held item / hand, the block-outline overlay, and weather effects (rain and snow). One thing is deliberately forced: backface culling is disabled for the duration of the capture, so geometry seen from "behind"  - the usual situation in a mirrored view  - still renders.

---

## A generic capture (security camera / monitor)

The bare-metal path is `PerspectiveCapture`. You position the virtual camera yourself, return `RENDER`, and pull the result out as a named texture your screen can sample.

```java
import com.meekdev.amnetic.client.scene.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

static final Identifier CAM_FEED =
    Identifier.fromNamespaceAndPath("mymod", "cam_feed");

PerspectiveCapture cam = PerspectiveCapture.builder()
    .resolution(0.5f) // half-res off-screen target
    .onCapture((ctx, view) -> {
        // is the player nowhere near a monitor? don't pay for it this frame.
        if (ctx.mainEye().distanceToSqr(MONITOR_POS) > 48 * 48) {
            return CaptureResult.SKIP; // keep last frame's image
        }

        view.eye(new Vec3(120, 70, 40)); // where the camera sits, world space
        view.viewRotation(lookSouth()); // a rotation matrix: which way it looks
        view.matchMainProjection(); // borrow the player's FOV / aspect
        view.distance((float) ctx.mainEye().distanceTo(MONITOR_POS)); // for the 4-cap priority
        return CaptureResult.RENDER;
    })
    .register(CAM_FEED); // also the texture id the feed lives under
```

The `Identifier` you pass to `register(...)` does double duty: it identifies the capture in the registry, and after each render the capture's color texture is published under that same id through the texture manager. So anything that samples a texture  - a screen quad, a model, a GUI  - can reference `mymod:cam_feed` and get the live feed.

To draw it onto a screen in the world, use `CaptureSurface` (below)  - a built-in placed quad that samples the feed for you, no shader required. If you need something it doesn't cover, you can always build a raw instanced surface (see [Instanced Rendering](Instanced-Rendering)) whose fragment shader samples the feed texture; the mirror walkthrough below shows that shape end to end.

### What the callback gets, and what it must do

Your callback runs **once per frame, per registered capture**, before the world is drawn. It gets:

- `ctx`  - a `CaptureContext` with the player's `mainEye()`, `mainViewRotation()`, `mainProjection()`, the `mainCamera()`, and `partialTicks()`. All read-only snapshots of the real frame.
- `view`  - a fresh `PerspectiveView` to fill in. It is `reset()` to identity/zero before your callback runs every frame, so you start from a clean slate and only set what you need.

You return `RENDER` to render this frame, or `SKIP` to skip it. **`SKIP` is free**  - no world render happens  - and it leaves the previously captured texture exactly as it was, so a sampler keeps showing the last good image rather than going black. Use it generously: offscreen, too far, occluded, paused, whatever. `SKIP` is how you keep the cost down.

---

## `PerspectiveView` in full

Everything the virtual camera needs lives here. Each setter returns `this`, so you can chain. Remember it is reset to defaults (identity matrices, zero eye, no clip) before every callback.

```java
view.eye(Vec3 pos); // virtual camera position, world space
view.viewRotation(Matrix4fc m); // orientation as a view-rotation matrix
view.projection(Matrix4fc m); // an explicit projection matrix...
view.matchMainProjection(); // ...or borrow the player's projection this frame
view.clipPlane(Vector3f n, float d);// optional near clip plane (also a float overload)
view.clipPlane(float nx, float ny, float nz, float d);
view.distance(float d); // priority hint for the 4-capture cap
```

- **`eye`**  - where the camera is, in world coordinates.

- **`viewRotation`**  - a rotation matrix describing which way the camera faces, in the same convention as Minecraft's `cameraRenderState.viewRotationMatrix`. The capture derives a yaw/pitch from this to pose the actual `Camera`, so entities, particles, and billboards orient correctly. The simplest case is copying the player's: `view.viewRotation(ctx.mainViewRotation())`.

- **`projection` vs `matchMainProjection`**  - these are mutually exclusive; whichever you call last wins. `projection(m)` gives an explicit projection (a custom FOV, a narrow lens, an orthographic feed). `matchMainProjection()` says "use whatever projection the player's camera has this frame," which keeps FOV and aspect ratio in sync. Calling `projection(...)` clears the match flag; calling `matchMainProjection()` sets it.

- **`clipPlane`**  - an optional world-space plane `(n, d)` where a point `p` is kept when `dot(n, p) + d >= 0`. When set, the capture folds it into the projection as an **oblique near-clip plane**, so everything behind that plane is clipped away at the hardware level. This is what stops geometry *behind* a mirror from bleeding into the reflection. Both overloads do the same thing; one takes a `Vector3f` normal, the other three floats. For a reflection across a plane through point `pt` with normal `n`, the right `d` is `-(n · pt)`. Leaving the clip plane unset (the default) just renders the full frustum.

- **`distance`**  - a sorting hint, nothing more. When more captures want to render than the per-frame cap allows (four), the ones with the smallest `distance` win. Set it to roughly how far the viewer is from the thing being captured. It does not affect the render itself.

---

## Getting the result out: `CaptureTarget`

`capture.target()` hands you the off-screen target the capture renders into. Most of the time you don't touch this directly  - `registerColor(id)` happens automatically each frame using the capture's own id  - but it's here when you need the raw handles.

```java
CaptureTarget t = cam.target();
int color = t.colorTextureGlId(); // raw GL texture id of the color attachment (0 if none yet)
int depth = t.depthTextureGlId(); // raw GL texture id of the depth attachment
int w = t.width(); // actual pixel width  (window * resolution)
int h = t.height(); // actual pixel height
t.registerColor(myId); // publish the color texture under myId for samplers
```

- **`colorTextureGlId()` / `depthTextureGlId()`**  - the underlying OpenGL texture handles, for when you want to bind them yourself or feed them to a [Framebuffer](Framebuffers) / custom pass. They return `0` until the first render has allocated the target.

- **`registerColor(Identifier)`**  - bridges the color texture into Minecraft's texture manager under an id, so any sampler that takes an `Identifier` can read the live capture. This is the normal way to consume a capture. The capture does this for you with its own id after each successful render, but you can also register it under additional ids.

- **`width()` / `height()`**  - the real pixel size of the target. The target is sized to `round(windowSize * resolution)`, clamped to at least 1px, and it resizes automatically when the window does. So at `resolution(0.5f)` on a 1920x1080 window the target is 960x540. Both return `-1` until the first render has allocated the target.

The depth and color attachments persist between frames, which is exactly why `SKIP` leaves a usable image behind.

---

## `CaptureSurface`: putting a feed on a surface, the easy way

Once a capture is publishing its texture, you still need something in the world that shows it. You could build that yourself  - an instanced mesh, a custom shader that samples the feed  - but for the common case (a flat screen showing a camera feed) `CaptureSurface` already is that thing. It is a builder that registers a placed, instanced surface using the built-in `amnetic:scene/capture_surface` shader, with the feed bound as its sampler and optional masking on top.

```java
import com.meekdev.amnetic.client.scene.CaptureSurface;

CaptureSurface screen = CaptureSurface.builder(FEED) // the capture's texture id
    .at(SCREEN_POS, SCREEN_NORMAL, 1.5f, 1.0f) // center, outward normal, width, height
    .mask(CaptureSurface.Mask.none()) // default: show the whole feed
    .sampling(CaptureSurface.Sampling.FIT) // default: stretch feed across the quad
    .register(Identifier.fromNamespaceAndPath("mymod", "monitor_screen"));
```

That's a working monitor: a 1.5x1 quad at `SCREEN_POS`, facing `SCREEN_NORMAL`, showing the live `FEED` texture.

### Placement

Placement is required  - either fixed or per-frame:

- **`at(center, normal, width, height)`**  - a fixed surface: world-space center, outward normal (normalized for you), and size in blocks. Width and height must be positive.
- **`placement(ctx -> ...)`**  - a `PlacementSupplier` that returns a `Placement(center, normal, width, height)` each frame, computed from the `InstanceRenderContext`. Return `null` to skip drawing the surface that frame  - the surface analogue of `SKIP`.

### Sampling modes

- **`Sampling.FIT`** (default)  - the feed is mapped across the surface by the mesh's own local coordinates: the whole image, stretched to fit the quad. This is what a monitor or screen wants.
- **`Sampling.SCREEN`**  - a screen-space (projective) lookup, the same trick as the mirror's `screen_reflect` shader: each fragment samples the feed at its own screen position. Use this when the feed was captured from a view aligned with the viewer, e.g. portal-style effects.

### Masks

A mask shapes the visible area of the surface:

- **`Mask.none()`** (default)  - the full quad.
- **`Mask.circle()`**  - a circular cutout with a soft edge; overloads take a `feather` (edge softness, default `0.02`) and a `radius` (default `1`, the full quad). Good for portholes, scrying pools, lens effects.
- **`Mask.texture(alphaMaskId)`**  - an arbitrary alpha-mask texture; the feed shows where the mask is opaque. This is bound as a second sampler.

### Everything else

- **`geometry(MeshData)`**  - defaults to `MeshData.quad()`. The mesh must be **position-only**: surface UVs are derived from the local mesh coordinates, so `register` rejects geometry that carries its own texture coordinates.
- **`phase(InstancePhase)`**  - defaults to `WORLD_LAST`, the same phase as the mirror faces.
- **`register(surfaceId)`**  - registers the surface as an instanced mesh (depth test and write on, alpha blending, backface culling off) and returns the `CaptureSurface` handle.

The handle gives you runtime control: `setEnabled(false)` hides the surface without unregistering it (and `isEnabled()` reads it back), `remove()` disables it and unregisters the mesh for good, and `surfaceId()` returns the id it was registered under.

---

## `PlanarReflection`: mirrors, the easy way

Writing the reflection math by hand is fiddly. `PlanarReflection` does it for you: given a plane, it positions the virtual camera at the mirrored eye, mirrors the view orientation, optionally installs the oblique clip plane, and culls by distance. It is a thin builder over `PerspectiveCapture` plus a matching surface builder.

```java
import com.meekdev.amnetic.client.scene.PlanarReflection;
import net.minecraft.resources.Identifier;

static final Identifier REFLECTION =
    Identifier.fromNamespaceAndPath("mymod", "mirror");

PlanarReflection reflection = PlanarReflection.builder()
    .plane(mirrorCenter, mirrorNormal) // fixed plane...
 // .plane(ctx -> findPlane(ctx)) // ...or a per-frame supplier (return null to skip)
    .resolution(0.5f) // default 0.5
    .obliqueClip(true) // default true  - clip behind the mirror
    .activateWithin(64) // default -1 (always); skip past this distance
    .register(REFLECTION);

// then a surface that displays it:
reflection.surface()
    .quad(mirrorCenter, mirrorNormal, 1f, 1f) // a 1x1 reflective face
    .register(Identifier.fromNamespaceAndPath("mymod", "mirror_face"));
```

### The plane and the supplier

```java
record Plane(Vec3 point, Vector3f normal) {}
interface PlaneSupplier { Plane plane(CaptureContext ctx); } // return null to skip
```

A `Plane` is a point on the surface and an outward `normal` (the side the viewer is on). You can give a fixed plane via `plane(point, normal)`  - the builder normalizes the normal for you  - or a `PlaneSupplier` via `plane(ctx -> ...)` for a mirror that moves, turns, or has to be located in the world each frame. **Returning `null` from the supplier is the supplier's way of saying `SKIP`**: no mirror in range, nothing to reflect, leave last frame's texture.

### The builder options

- **`resolution(float)`**  - fraction of window size for the off-screen target. Default `0.5`. Reflections tolerate low resolution well; `0.5` or lower is usually invisible in motion and halves (or better) the cost.

- **`obliqueClip(boolean)`**  - default `true`. When on, the reflection capture installs an oblique near-clip plane coincident with the mirror plane, so geometry behind the mirror is clipped before it can be drawn. **Why it matters:** without it, objects sitting just behind or intersecting the mirror surface get reflected and smear into the image, and surfaces flush with the mirror plane fight for depth and shimmer (classic z-fighting). The oblique clip plane snaps the near plane to the mirror, eliminating both. Turn it off only if you specifically want to see through the mirror plane.

- **`activateWithin(double)`**  - default `-1`, meaning always active. Set a distance in blocks and the reflection returns `SKIP` whenever the player's eye is farther than that from the plane point. This is the cheapest, most important optimization for world-placed mirrors: a mirror you can't get near costs nothing.

- **`register(Identifier)`**  - wires the capture into the manager under `reflectionId` and returns the `PlanarReflection` handle. The reflected color texture is published under that id, which is the id the surface samples.

### The reflection math, briefly

You don't need this to use the system, but here's what `register` arranges each frame, computed in `ReflectionMath`:

- **Reflected eye**  - your eye position mirrored across the plane (`reflectPoint`): the component of the eye-to-plane vector along the normal is flipped.
- **Reflected view**  - the main view rotation composed with the Householder reflection matrix about the plane normal (`reflectedView`), so the mirrored camera looks "into" the mirror correctly.
- **Oblique projection**  - when `obliqueClip` is on, the plane is transformed into the mirrored camera's clip space and the projection's third row is rewritten so the near plane coincides with the mirror (`obliqueProjection`). It handles both `[0,1]` and `[-1,1]` depth conventions depending on the GPU device.

The capture then renders the world from that mirrored camera into the reflection target, and the surface shader projects the result back onto the quad.

### The reflective surface

`reflection.surface()` builds the quad that shows the reflection. Under the hood it's an [instanced mesh](Instanced-Rendering) using the built-in `PlanarReflection.SCREEN_SHADER` (`amnetic:scene/screen_reflect`), with the reflection texture bound as `ReflectionSampler`.

```java
Surface surface(); // from reflection.surface()
Surface quad(Vec3 center, Vector3f normal, float width, float height);
void register(Identifier faceId);
```

`quad(center, normal, width, height)` describes one reflective face: its center in world space, its outward normal, and its size. `register(faceId)` registers it as an instanced reflective face drawn in the `WORLD_LAST` phase, with depth test and write on, no blending, backface culling off.

The screen shader is the key to why a planar reflection looks right. The reflection was captured from the *mirrored* camera, so the correct UV for any fragment is simply its own position in the **player's** clip space, remapped to `[0,1]`:

```glsl
// screen_reflect.fsh
vec2 uv = (vClip.xy / vClip.w) * 0.5 + 0.5;
FragColor = vec4(texture(ReflectionSampler, uv).rgb, 1.0);
```

That is a screen-space (projective) lookup: each point on the mirror samples the reflection texture at the same screen location it occupies, which is exactly where its mirrored counterpart was rendered. See [Writing Shaders](Writing-Shaders) for the shader plumbing.

---

## A complete mirror walkthrough

This is the real example from the examples mod (`com.example.mirror`), trimmed to the load-bearing parts. It registers a `MirrorBlock` (a `HorizontalDirectionalBlock` with a `FACING` property), then on the client wires up the reflection and the faces. The full source is in the repository under `examples/src/main/java/com/example/mirror/`.

### 1. Client registration: reflection + faces

```java
public static void registerClient() {
    PlanarReflection.builder()
        .plane(MirrorFeature::findPlane) // locate the nearest mirror each frame
        .resolution(0.5f)
        .obliqueClip(true)
        .activateWithin(TRACK_DIST) // 64 blocks
        .register(REFLECTION_ID);

    // the reflective faces, as one instanced batch
    InstancedMesh.builder(BuiltinShader.TRANSFORM)
        .geometry(MeshData.quad())
        .shaders(PlanarReflection.SCREEN_SHADER, PlanarReflection.SCREEN_SHADER)
        .extraSampler("ReflectionSampler", REFLECTION_ID, 1)
        .renderState(RenderState.builder()
            .depthTest(true).depthWrite(true)
            .blend(RenderState.BlendMode.NONE)
            .backfaceCulling(false)
            .build())
        .phase(InstancePhase.WORLD_LAST)
        .onRender(MirrorFeature::renderFaces)
        .register(FACE_ID);
}
```

Note the example builds its own `InstancedMesh` rather than using `reflection.surface()`. That's deliberate: a real mirror is often several blocks wide, so it emits **one transform instance per mirror block** in a single batch, instead of a lone quad. The `surface()` helper is the one-quad convenience; this is the same idea scaled up. Either way it uses the same `SCREEN_SHADER` and binds the reflection under `ReflectionSampler`.

### 2. Finding the plane each frame (`PlaneSupplier`)

`findPlane(ctx)` is the supplier. It finds the nearest mirror block, builds the plane from that block's face, and returns `null` when there's nothing to reflect  - which is the supplier's `SKIP`.

```java
private static PlanarReflection.Plane findPlane(CaptureContext ctx) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) return null;
    Vec3 eye = ctx.mainEye();

    // ...prefer the already-tracked mirror if still in range,
    //    else scan a small box around the eye for the nearest MirrorBlock...

    if (best == null) { // none found
        trackedPos = null; facing = null; surface.clear();
        return null; // SKIP  - keep last frame's reflection
    }

    trackedPos = best;
    facing = bestFacing;
    collectSurface(mc, best, bestFacing); // flood-fill the contiguous mirror face

    Vec3i n = bestFacing.getUnitVec3i(); // plane point is half a block off the face
    Vec3 point = new Vec3(best.getX() + 0.5 + n.getX() * 0.5,
                          best.getY() + 0.5 + n.getY() * 0.5,
                          best.getZ() + 0.5 + n.getZ() * 0.5);
    return new PlanarReflection.Plane(point, new Vector3f(n.getX(), n.getY(), n.getZ()));
}
```

The plane point is pushed half a block out from the block center along the facing direction, so it sits on the visible surface. The normal is the block's facing. `collectSurface` flood-fills all contiguous mirror blocks on the same face into a `surface` list, so a wall of mirrors reflects as one plane.

### 3. Rendering the faces

`renderFaces` runs in the instanced render phase. It builds an orthonormal basis from the face normal and emits one transform per collected mirror block, each offset a hair (`0.501`) off the block so it sits just proud of the surface and doesn't z-fight the block model.

```java
private static void renderFaces(InstanceRenderContext ctx,
                                InstanceBatch<BuiltinShader.Transform> batch) {
    if (facing == null || surface.isEmpty()) return;

    Vector3f n = /* face normal, normalized */;
    Vector3f right = new Vector3f(n).cross(0f, 1f, 0f);
    if (right.lengthSquared() < 1e-6f) right.set(1f, 0f, 0f);
    right.normalize();
    Vector3f up = new Vector3f(right).cross(n).normalize();

    Vec3 cam = ctx.cameraPos();
    for (BlockPos pos : surface) {
        float cx = (float) (pos.getX() + 0.5 + n.x * 0.501 - cam.x);
        float cy = (float) (pos.getY() + 0.5 + n.y * 0.501 - cam.y);
        float cz = (float) (pos.getZ() + 0.5 + n.z * 0.501 - cam.z);

        Matrix4f m = new Matrix4f();
        m.m00(right.x); m.m01(right.y); m.m02(right.z); // x axis -> right
        m.m10(n.x);     m.m11(n.y);     m.m12(n.z); // y axis -> normal
        m.m20(up.x);    m.m21(up.y);    m.m22(up.z); // z axis -> up
        m.m30(cx);      m.m31(cy);      m.m32(cz); // translate, camera-relative
        batch.add(new BuiltinShader.Transform(m));
    }
}
```

Coordinates are camera-relative (positions minus `cam`) because that's the space the world is rendered in. That's the whole mirror: a `PlaneSupplier` that finds the surface, a `PlanarReflection` that does the math and captures the reflected world, and an instanced batch of faces that sample the result through the screen shader.

To make this work in your own mod you register the block + item server-side (`MirrorFeature.register()`), call `registerClient()` on the client init, and ship the block model/textures. The example wires these from `ExamplesMod` and `ExamplesClient`.

---

## A second example: a monitor fed by a remote camera

The same machinery, without reflection math, gives you a CCTV monitor. A `PerspectiveCapture` sits at a fixed point looking in a fixed direction, and a `CaptureSurface` displays the feed.

```java
static final Identifier FEED = Identifier.fromNamespaceAndPath("mymod", "cctv");

// 1) the camera: fixed eye, fixed look, only render when a viewer is near the monitor
PerspectiveCapture.builder()
    .resolution(0.4f)
    .onCapture((ctx, view) -> {
        Vec3 eye = ctx.mainEye();
        double toMonitor = eye.distanceTo(MONITOR_POS);
        if (toMonitor > 24) return CaptureResult.SKIP; // nobody's watching

        view.eye(CAMERA_POS);
        view.viewRotation(CAMERA_LOOK_MATRIX); // your own look matrix
        view.matchMainProjection(); // or projection(...) for a tighter lens
        view.distance((float) toMonitor); // priority among captures
        return CaptureResult.RENDER;
    })
    .register(FEED);

// 2) the screen: a CaptureSurface on the monitor's face, showing the feed
CaptureSurface.builder(FEED)
    .at(MONITOR_POS, MONITOR_NORMAL, 1.5f, 1.0f)
    .register(Identifier.fromNamespaceAndPath("mymod", "cctv_screen"));
```

The only conceptual difference from the mirror is the sampling: a monitor reads the feed by its own quad UVs (`Sampling.FIT`, the default), where a mirror reads it by screen-space clip coordinates. Everything else  - the capture, the `SKIP` budgeting, the texture publishing  - is identical.

---

## Performance and pitfalls

- **Every `RENDER` is a full world render.** Treat captures as expensive. Budget them. The single biggest lever is returning `SKIP` whenever the result isn't on screen  - too far, behind the player, occluded, in a menu.

- **Cap is four per frame.** Beyond four simultaneous `RENDER`s, only the four nearest by `distance()` actually render; the rest hold their last image. Set `distance()` honestly so the right ones win.

- **No recursion  - mirrors don't reflect mirrors.** Captures don't run nested inside a capture (the manager guards against re-entrancy, and the redirect only applies one level deep). A mirror seen inside another mirror's reflection will show its last captured frame, not a fresh recursive reflection. Infinite-mirror corridors won't recurse.

- **Resolution is your friend.** Default `0.5` is a 4x pixel saving over full-res and is usually invisible for reflections and feeds. Drop to `0.35`-`0.4` for small or distant screens.

- **Use `activateWithin` for world-placed mirrors.** A mirror you can't get near should cost nothing. Pair it with a per-frame `SKIP` when the surface is off-screen for the best result.

- **If a capture throws, all captures are disabled** for the rest of the session and the error is logged under `Amnetic/Capture`. This is intentional: one broken capture won't take down the whole frame. Check the log if your reflections suddenly stop updating.

- **Set `distance()` even for a single capture.** It costs nothing and makes the priority deterministic if you ever add a second capture.

---

## Reference

### `PerspectiveCapture`

```java
static Builder builder();

Identifier id();
float resolution();
PerspectiveView view();
CaptureTarget target();
CaptureResult requestView(CaptureContext ctx); // internal: resets view, runs your callback

@FunctionalInterface
interface CaptureFunction { CaptureResult capture(CaptureContext ctx, PerspectiveView view); }

// Builder
Builder resolution(float scale); // fraction of window size; default 0.5; must be > 0
Builder onCapture(CaptureFunction callback); // required
PerspectiveCapture register(Identifier id); // registers the capture; id also names its texture
```

### `PerspectiveView` (chainable, reset before every callback)

```java
PerspectiveView eye(Vec3 pos);
PerspectiveView viewRotation(Matrix4fc matrix);
PerspectiveView projection(Matrix4fc matrix); // clears matchMainProjection
PerspectiveView matchMainProjection(); // clears explicit projection
PerspectiveView clipPlane(Vector3f normal, float d);
PerspectiveView clipPlane(float nx, float ny, float nz, float d);
PerspectiveView distance(float distance); // priority hint for the 4-capture cap
```

### `CaptureContext` (read-only)

```java
Camera mainCamera();
float partialTicks();
Vec3 mainEye();
Matrix4f mainViewRotation();
Matrix4f mainProjection();
```

### `CaptureResult`

```java
RENDER // capture this frame (full world render)
SKIP // skip this frame; keep the previous texture (free)
```

### `CaptureTarget` (via `capture.target()`)

```java
int colorTextureGlId(); // raw GL color texture id (0 before first render)
int depthTextureGlId(); // raw GL depth texture id
void registerColor(Identifier id); // publish the color texture under id, for samplers
int width(); // round(windowWidth  * resolution), min 1 (-1 before first render)
int height(); // round(windowHeight * resolution), min 1 (-1 before first render)
```

### `CaptureSurface`

```java
static final Identifier SHADER; // amnetic:scene/capture_surface

enum Sampling { FIT, SCREEN } // quad UVs vs screen-space projective

// Mask
static Mask none();
static Mask circle(); // feather 0.02, radius 1
static Mask circle(float feather);
static Mask circle(float feather, float radius);
static Mask texture(Identifier alphaMask);

record Placement(Vec3 center, Vector3f normal, float width, float height) {}
@FunctionalInterface
interface PlacementSupplier { Placement placement(InstanceRenderContext ctx); } // null = skip

static Builder builder(Identifier feedId); // the capture texture to display

// Builder
Builder geometry(MeshData geometry); // default MeshData.quad(); must be position-only
Builder mask(Mask mask); // default Mask.none()
Builder sampling(Sampling sampling); // default FIT
Builder phase(InstancePhase phase); // default WORLD_LAST
Builder placement(PlacementSupplier supplier); // per-frame placement...
Builder at(Vec3 center, Vector3f normal, float width, float height); // ...or fixed
CaptureSurface register(Identifier surfaceId);

// handle
Identifier surfaceId();
boolean isEnabled();
CaptureSurface setEnabled(boolean enabled);
void remove(); // disable + unregister the mesh
```

### `PlanarReflection`

```java
record Plane(Vec3 point, Vector3f normal) {}
@FunctionalInterface
interface PlaneSupplier { Plane plane(CaptureContext ctx); } // return null to skip

static final Identifier SCREEN_SHADER; // amnetic:scene/screen_reflect

static Builder builder();
Identifier reflectionId();
Surface surface(); // from a registered PlanarReflection

// Builder
Builder plane(Vec3 point, Vector3f normal); // fixed plane (normal is normalized for you)
Builder plane(PlaneSupplier supplier); // per-frame plane; null = skip
Builder resolution(float scale); // default 0.5
Builder obliqueClip(boolean enabled); // default true
Builder activateWithin(double distance); // default -1 (always active)
PlanarReflection register(Identifier reflectionId);

// Surface (via reflection.surface())
Surface quad(Vec3 center, Vector3f normal, float width, float height);
void register(Identifier faceId); // registers the reflective face (WORLD_LAST)
```

---

## See Also

- [Framebuffers](Framebuffers). The off-screen targets a capture renders into, and how to bind their raw textures into your own passes.

- [Instanced Rendering](Instanced-Rendering). How the reflective surface (and any screen) is drawn, and how `extraSampler` binds the capture texture.

- [Writing Shaders](Writing-Shaders). The `screen_reflect` shader and how to write your own that samples a captured feed.
