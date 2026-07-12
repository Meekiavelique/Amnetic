# Framebuffers

A framebuffer is an off-screen render target you own. Instead of drawing to the screen, you draw into a texture you allocated, then read that texture back: sample it in a shader, copy it somewhere, run another pass over it. It is the plumbing the bloom, deferred-light, scene-capture, and entity-effect systems all sit on, and it is exposed so you can build your own screen-space effects without touching raw OpenGL.

The mental model is small. A `Framebuffer` is one or more *color* textures, plus an optional *depth* attachment, wrapped in a GL framebuffer object. You `begin()` it (binds it and points the viewport at it), draw, `clear()` if you need to, `end()` it (restores whatever was bound before), and then the color and depth live in textures you can sample. When you are done with it for good, you `dispose()` it so the GPU memory goes back.

- **`Framebuffer`** is one render target. The thing you begin, draw into, end, and sample.
- **`Framebuffers`** is the factory. You almost always make targets through here rather than the constructors.
- **`FramebufferSpec`** describes the attachments  - how many color textures, what pixel format each one is, and whether there is depth. Built with a small builder.
- **`ColorFormat`** / **`DepthMode`** are the format enums that go into a spec.
- **`PingPongBuffer`** is a pair of matched targets that swap each pass, for read-one/write-the-other filters like a separable blur.
- **`FramebufferException`** is the unchecked exception everything throws on misuse.

A few things to keep in mind before the details:

- A **screen** framebuffer resizes itself to track the window; a **fixed** one stays the exact size you gave it.
- Allocation is **lazy**. No GPU memory is taken until the first call that needs it (`begin()`, a `blit...`, or `registerColorTexture`). That first call is also where a screen target reads the current window size.
- Everything here is **render-thread** work. The methods make raw GL calls against the live context; call them from the render thread, not a tick or network thread.
- You own the lifetime. Call `dispose()` when you are done, or hand the target to a system that disposes it. Anything still alive at client shutdown is force-disposed for you (see Status), but that is a safety net, not a plan.

---

## Making one

```java
import com.meekdev.amnetic.client.framebuffer.*;

// half-resolution HDR color + a readable depth texture, tracks the window
Framebuffer fb = Framebuffers.screen(0.5f, FramebufferSpec.builder()
        .color(ColorFormat.RGBA16F)
        .depthTexture()
        .build());

// a fixed 512x512 LDR target that never resizes
Framebuffer thumb = Framebuffers.fixed(512, 512, FramebufferSpec.builder()
        .color(ColorFormat.RGBA8)
        .build());
```

`Framebuffers.screen(spec)` is the same as `screen(1.0f, spec)`  - full window resolution. The `scale` is a multiplier on the main render target's pixel size: `0.5f` is half on each axis (a quarter of the pixels), `2.0f` is supersampled. It must be greater than zero, or `screen(...)` throws a `FramebufferException` immediately. The size is computed lazily as `max(1, round(main.width * scale))` by `max(1, round(main.height * scale))`, re-read every time the target is touched, so a screen target follows window resizes on its own  - when the window changes size, the next `begin()`/`blit`/`register` reallocates the textures to match. A fixed target ignores the window entirely and always allocates the width and height you passed.

Two shortcuts cover the common "grab the current frame" cases. Both are full-resolution screen targets:

```java
Framebuffer color = Framebuffers.captureColor(); // RGBA8 screen target, no depth
Framebuffer scene = Framebuffers.captureDepth(); // RGBA8 color + a depth texture
```

### The spec

`FramebufferSpec` is just the description of the attachments; one spec can be reused to build several targets.

```java
FramebufferSpec spec = FramebufferSpec.builder()
        .color(ColorFormat.RGBA16F) // attachment 0
        .color(ColorFormat.R8) // attachment 1 (MRT)
        .depthRenderbuffer() // depth, write-only
        .build();
```

- Call `color(...)` once per color attachment. The first becomes attachment 0, the next attachment 1, and so on. Multiple color attachments give you **MRT** (multiple render targets): one draw writes several outputs, which your fragment shader addresses as `layout(location = N)`. All draw buffers are enabled, so a shader that only writes location 0 leaves the others untouched.
- The cap is `FramebufferSpec.MAX_COLOR_ATTACHMENTS`, which is **8**. `build()` throws if you exceed it.
- `build()` also throws if you added **zero** color attachments  - every framebuffer needs at least one. A color-less depth-only target is not supported here.
- Depth is opt-in. By default a spec has `DepthMode.NONE` and no depth attachment. `depthTexture()` and `depthRenderbuffer()` each set the mode; calling both just keeps whichever you called last.

#### Color formats

Each `ColorFormat` is an internal GL format plus its per-pixel byte cost (used below for the memory math). Pick by what the attachment holds:

| Format | Bytes/pixel | What it is | Reach for it when |
|---|---|---|---|
| `RGBA8` | 4 | 8-bit per channel, normalized 0..1 (LDR) | The default workhorse  - plain color, UI, anything that fits in 0..1. |
| `RGBA16F` | 8 | 16-bit half-float per channel (HDR) | Bloom, light accumulation, anything that goes above 1.0 or needs smooth gradients. |
| `R11G11B10F` | 4 | Packed float RGB, no alpha | HDR color where you do not need alpha and want half the bytes of `RGBA16F`. |
| `R8` | 1 | Single 8-bit channel | Masks, occlusion, a single scalar field  - cheap. |

All color textures are created with linear min/mag filtering and clamp-to-edge wrapping, so sampling them with normalized UVs gives you bilinear filtering and edges that do not wrap.

#### Depth: texture vs renderbuffer

Depth has three modes, chosen on the spec:

- **`NONE`** (default)  - no depth buffer. Depth testing and depth writes have nowhere to go, so this is for pure 2D/fullscreen passes that do not need occlusion.
- **`depthTexture()`** (`DepthMode.TEXTURE`)  - depth is a real `DEPTH_COMPONENT24` texture you can **sample**. Use this when a later pass needs to read the scene depth (reconstructing world position, soft particles, fog, deferred lighting). Read its GL id with `depthTextureGlId()`.
- **`depthRenderbuffer()`** (`DepthMode.RENDERBUFFER`)  - depth is a `DEPTH_COMPONENT24` renderbuffer. It works for depth testing while you draw, but it is **write-only from your side**: you cannot sample it, and `depthTextureGlId()` returns `0`. It is slightly cheaper and is the right pick when you need depth *testing* during the pass but never need to read depth back.

When depth is present, `clear()` clears it to `1.0` alongside the color. When the mode is `NONE`, `clear()` only touches color.

After `build()` finishes the validation, the heavy lifting still has not happened: the spec is just a recipe. The actual GL objects are created on first allocation, and if the resulting framebuffer is somehow incomplete (a bad format combination on your driver), the allocating call throws a `FramebufferException` with the GL completeness status.

---

## Drawing into it and reading it back

The core loop is begin / clear / draw / end:

```java
fb.begin(); // allocates if needed, binds the FBO, sets the viewport to the target size
fb.clear(0f, 0f, 0f, 0f); // clear color (and depth to 1.0 if the spec has depth)
// ... your draws land in fb's textures ...
fb.end(); // re-binds whatever FBO was bound before, viewport back to the main target's size
```

What each call changes:

- `begin()` first does the lazy allocation, then **saves** the currently bound draw framebuffer, binds this target's FBO, and sets the viewport to `(0, 0, width, height)`. The viewport to restore is not queried from the driver (a `glGetIntegerv` there would stall the pipeline); it is derived as the main render target's full size, falling back to the query only when the main target is absent. It does **not** clear anything and does **not** touch blend, depth-test, cull, or shader state  - that is yours to manage around the draw.
- `clear(r, g, b, a)` sets the clear color and clears the color buffer; if the spec has any depth, it also sets the clear depth to `1.0` and clears depth in the same call. Call it after `begin()`.
- `end()` restores the saved framebuffer binding and points the viewport back at the main target's full size (not whatever viewport happened to be current before `begin()`  - nest your own viewport tricks accordingly). If you call `end()` without a matching `begin()` it is a no-op (the saved state is empty). It does not restore blend/depth/cull state, so if your pass changed those, restore them yourself.

Once you have drawn, the results live in textures. Read their raw GL ids and use them however you like:

```java
int colorTex = fb.colorTextureGlId(0); // GL id of color attachment 0
int colorTex1 = fb.colorTextureGlId(1); // attachment 1, if it's an MRT spec
int depthTex = fb.depthTextureGlId(); // GL id of the depth texture, or 0 if not a depth-texture target
```

`colorTextureGlId(index)` returns the GL texture id of the color attachment at that index  - pass it to your own sampler binding, a blit, or a fullscreen pass. `depthTextureGlId()` returns the depth texture's id, or `0` if the target has no depth or uses a renderbuffer (which cannot be sampled).

For the common case of feeding color attachment 0 into a shader sampler, there is a convenience:

```java
fb.bindSampler(2); // activate texture unit 2 and bind color attachment 0 to it
```

`bindSampler(unit)` activates `GL_TEXTURE0 + unit` and binds color attachment 0 there as a `GL_TEXTURE_2D`. It is a thin helper  - it binds nothing else, and it only ever exposes attachment 0. For other attachments or the depth texture, bind the id from `colorTextureGlId`/`depthTextureGlId` yourself.

### Exposing a target by name to the resource system

If you want a shader to refer to your capture by `Identifier` the way it refers to vanilla samplers, register it:

```java
import net.minecraft.resources.Identifier;

fb.registerColorTexture(Identifier.fromNamespaceAndPath("mymod", "my_capture"));
```

`registerColorTexture(id)` allocates if needed, wraps color attachment 0 in a texture that Minecraft's `TextureManager` understands, and registers it under that `Identifier`. From then on the engine can resolve that id to your live color texture. The bridge is idempotent  - the first call registers, and every subsequent call just **updates** the wrapped texture to point at the current GL id, width, and height. That matters for screen targets: after a window resize the underlying color texture is a new GL object, so you should call `registerColorTexture` again (or once per frame) so the registered name keeps pointing at the live texture rather than a stale, deleted one. The wrapper is described to the engine as `RGBA8`; this is the texture-view format the registry advertises, independent of the attachment's actual internal format.

### Blitting to and from the main render target

Sometimes you do not want to *draw* into the target, you want to *copy* pixels into or out of it. Four blits cover that, and they are how the scene gets captured mid-frame. Each one allocates on demand and is a no-op if Minecraft's main render target is unavailable.

```java
fb.blitColorFromMain(); // copy the live frame's color into color attachment 0
fb.blitDepthFromMain(); // copy the live frame's depth into this target's depth attachment
fb.blitColorToMain(); // copy this target's color attachment 0 back onto the screen
fb.blitDepthFrom(srcDepthGlId, srcW, srcH); // copy depth from an arbitrary depth texture you name
```

- `blitColorFromMain()` reads the main target's color texture and blits it into this target's color attachment 0, scaling from the main size to this target's size with **linear** filtering. This is the move for "snapshot what's on screen right now so I can post-process it." It works regardless of your target's scale, since the blit rescales.
- `blitDepthFromMain()` reads the main target's depth and blits it into this target's depth attachment with **nearest** filtering (depth must not be linearly filtered). It is a no-op if the main target has no depth. Your target obviously needs a depth attachment for this to land anywhere useful.
- `blitColorToMain()` is the reverse: it blits this target's color attachment 0 back onto the main render target's color, linearly. Use it to push a processed result back onto the screen.
- `blitDepthFrom(srcDepthGlId, srcW, srcH)` blits depth from a depth texture you identify by GL id and size, into this target's depth, nearest-filtered. It is a no-op if any argument is non-positive. Use it when the source depth is not the main target  - for example a depth snapshot you captured earlier in the frame.

All of these manage their own read/write FBO bindings internally and detach the source texture afterward, so they never leave a dangling reference to a main texture that might get resized or destroyed. They restore the previously bound draw framebuffer when they finish, so a blit does not leave your target bound  - if you want to *draw* after a `blitColorFromMain()`, call `begin()` again.

### Disposing

```java
fb.dispose();
```

`dispose()` deletes every GL object the target owns  - all color textures, the depth texture or renderbuffer, the main FBO, and the helper read/write FBOs used by the blits  - and removes it from the live registry. It is idempotent: calling it twice does nothing the second time. After disposal the target is dead; any call that needs allocation (`begin()`, a blit, `registerColorTexture`) throws `FramebufferException("Framebuffer used after dispose()")`.

If you forget to dispose, the GPU memory leaks for the lifetime of the client. Every color attachment is `width * height * pixelBytes` of VRAM, the depth attachment is roughly `width * height * 3` more (24-bit depth), and a window-tracking target reallocates that whole footprint on each resize  - so a leaked screen target is a leak that grows as the window grows. The client-shutdown hook force-disposes anything still registered, but that only fires when the game closes; it will not save you from leaking across a world reload.

---

## Ping-pong passes

A separable blur  - or any filter that reads one image and writes a transformed copy  - needs two buffers: you read A and write B, then read B and write A, and so on. `PingPongBuffer` is that pair, with the swap handled for you. Both buffers are **screen** targets built from the same spec and scale.

```java
PingPongBuffer pp = new PingPongBuffer(0.5f, FramebufferSpec.builder()
        .color(ColorFormat.RGBA16F)
        .build());

pp.pass(6, (read, write) -> {
    write.begin();
    bindTexture(read.colorTextureGlId(0)); // sample the previous result
    blurShader.draw(); // your fullscreen pass
    write.end();
});

Framebuffer result = pp.read(); // the buffer holding the final result after all passes
pp.dispose(); // disposes both internal targets
```

`pass(passes, body)` runs `body` exactly `passes` times. Each time it hands you the current **read** buffer and the current **write** buffer; you draw into `write` while sampling `read`. After each iteration it swaps them, so the buffer you just wrote becomes the read source for the next iteration. `passes` must be at least 1  - `0` or negative throws a `FramebufferException`. The single-arg constructor `new PingPongBuffer(spec)` is full resolution (scale 1.0).

After `pass(...)` returns, `read()` gives you the buffer that holds the final output (the swap leaves the last-written buffer as the read buffer). Sample its color attachment 0, or `blitColorToMain()` it back to the screen.

One detail worth internalizing: you only ever fill the *first* read buffer's contents yourself. The very first iteration reads whatever was last in buffer A  - so for a blur you typically seed buffer A before the loop (e.g. `pp.read().blitColorFromMain()` or draw your source into it), or treat the first pass specially. After that the ping-pong feeds itself.

---

## Worked example 1  - capture the scene and sample it

Grab the current frame's color into a target and register it so a shader can read it by name. This is exactly what the entity-effect scene snapshot does.

```java
import com.meekdev.amnetic.client.framebuffer.*;
import net.minecraft.resources.Identifier;

class SceneGrab {
    static final Identifier ID = Identifier.fromNamespaceAndPath("mymod", "scene_color");
    private Framebuffer capture;

    void capture() { // call mid-frame, on the render thread
        if (capture == null) {
            capture = Framebuffers.captureColor(); // RGBA8 screen target
        }
        capture.blitColorFromMain(); // snapshot the live frame's color
        capture.registerColorTexture(ID); // keep the named texture pointing at the live id
    }

    void dispose() {
        if (capture != null) { capture.dispose(); capture = null; }
    }
}
```

Now any pass that resolves `mymod:scene_color` samples a frozen copy of the scene as it looked at capture time. Because the target tracks the window and `registerColorTexture` re-points the name each call, the binding stays valid across resizes. If you also need depth (to reconstruct world position), build the target with `Framebuffers.captureDepth()` instead and bind `capture.depthTextureGlId()` as a second sampler  - that is the shape the deferred-light pass uses: `blitColorFromMain()` + `blitDepthFromMain()` into an RGBA8 + depth-texture target, then bind both into a fullscreen shader.

---

## Worked example 2  - a separable blur with `PingPongBuffer`

A two-tap separable Gaussian: pass 0 blurs horizontally, pass 1 vertically, repeated for a wider radius. The shader takes a `Direction` uniform that flips between horizontal and vertical.

```java
PingPongBuffer pp = new PingPongBuffer(0.5f, FramebufferSpec.builder()
        .color(ColorFormat.RGBA16F)
        .build());

// seed: put the source image into the first read buffer
pp.read().blitColorFromMain();

float[][] dirs = { {1f, 0f}, {0f, 1f} }; // horizontal, then vertical
int[] dir = {0};

pp.pass(4, (read, write) -> {
    write.begin();
    setupFullscreenState(); // disable blend/depth/cull, bind your VAO
    bindTexture(0, read.colorTextureGlId(0));
    blur.begin();
    blur.setSampler("Sampler", 0);
    blur.setVec2("TexelSize", 1f / read.width(), 1f / read.height());
    blur.setVec2("Direction", dirs[dir[0] & 1][0], dirs[dir[0] & 1][1]);
    blur.draw();
    write.end();
    dir[0]++;
});

// push the blurred result back to screen
pp.read().blitColorToMain();
pp.dispose();
```

The pattern that makes this work: each iteration samples `read.colorTextureGlId(0)`, writes into `write`, and the buffer swap means the next iteration reads what you just produced. `TexelSize` is computed from the *read* buffer's live dimensions so the kernel stays correct after resizes. Note you manage GL pipeline state (blend, depth, cull, the fullscreen VAO and shader) yourself around the draw  - `begin()`/`end()` only handle the FBO binding and viewport.

### A note on the shader side

A fullscreen pass over a framebuffer is an ordinary post shader: a trivial vertex shader emitting a full-screen triangle/quad, and a fragment shader that samples your color (and maybe depth) texture. Bind the framebuffer's texture to a unit, set the matching `sampler2D` uniform to that unit, and draw. For MRT, your fragment shader declares `layout(location = 0) out vec4 ...; layout(location = 1) out vec4 ...;` and each output lands in the matching color attachment. Sampling a `depthTexture()` attachment gives you non-linear window-space depth in `[0,1]`; linearize it with the projection if you need view-space distance. See [Writing Shaders](Writing-Shaders) for the GLSL conventions.

---

## Common pitfalls

- **Use after dispose.** Once you `dispose()`, every allocating call throws. If you cache a target across world reloads, make sure your reload path nulls the handle and rebuilds, rather than reusing a disposed one.
- **Forgetting `end()`.** If you `begin()` and never `end()`, you leave your FBO bound and the viewport pointed at the target  - the rest of the frame draws into your texture instead of the screen. Always pair them, ideally in a `try/finally`.
- **Sampling the target you are drawing into.** You cannot read and write the same texture in one pass; the result is undefined. That is the whole reason `PingPongBuffer` exists  - use two buffers and swap.
- **Assuming a registered name stays valid after a resize.** A screen target's color texture is a *new* GL object after the window changes size. Re-call `registerColorTexture` (or do it every frame) so the name follows the live texture.
- **Expecting `depthTextureGlId()` to work with a renderbuffer.** Renderbuffer depth is not sampleable; `depthTextureGlId()` returns `0`. Use `depthTexture()` if you need to read depth.
- **Calling `bindSampler` for a non-zero attachment.** It only ever binds attachment 0. For MRT outputs or depth, bind the explicit GL id yourself.
- **Off-thread calls.** These methods make immediate GL calls. Calling them from a tick or network thread will corrupt the GL context or crash. Marshal to the render thread first.

---

## Reference

### `Framebuffers`

```java
static Framebuffer fixed(int width, int height, FramebufferSpec spec); // exact pixel size, never resizes
static Framebuffer fixed(String name, int width, int height, FramebufferSpec spec);
static Framebuffer screen(FramebufferSpec spec); // window size (scale 1.0)
static Framebuffer screen(String name, FramebufferSpec spec);
static Framebuffer screen(float scale, FramebufferSpec spec); // window size * scale; scale must be > 0
static Framebuffer screen(String name, float scale, FramebufferSpec spec);
static Framebuffer captureColor(); // RGBA8 screen target, no depth
static Framebuffer captureDepth(); // RGBA8 color + depth texture, screen target
```

The `name` overloads take a human-readable label for the target, which shows up in debug listings of the live registry; the unnamed overloads are otherwise identical.

### `Framebuffer`

```java
// also constructable directly:
static Framebuffer createFixed(int width, int height, FramebufferSpec spec);
static Framebuffer createFixed(String name, int width, int height, FramebufferSpec spec);
static Framebuffer createScreen(float scale, FramebufferSpec spec);
static Framebuffer createScreen(String name, float scale, FramebufferSpec spec);

int width(); // current allocated width (0 before first allocation)
int height(); // current allocated height
boolean isAllocated(); // true once the GL objects exist (after the first begin()/blit/register)
int colorTextureGlId(int index); // GL id of color attachment `index`
int depthTextureGlId(); // GL id of the depth texture, or 0 if none / renderbuffer

void begin(); // allocate if needed, save+bind FBO, set viewport to target size
void clear(float r, float g, float b, float a); // clear color (+ depth to 1.0 if the spec has depth)
void end(); // restore the previously bound FBO; viewport back to the main target's full size
void bindSampler(int unit); // activate GL_TEXTURE0+unit and bind color attachment 0

void blitColorFromMain(); // main color -> this color 0 (linear, rescaled)
void blitColorToMain(); // this color 0 -> main color (linear, rescaled)
void blitDepthFromMain(); // main depth -> this depth (nearest); no-op if no main depth
void blitDepthFrom(int srcDepthGlId, int srcW, int srcH); // arbitrary depth tex -> this depth (nearest)

void registerColorTexture(Identifier id); // expose color 0 to the texture registry under `id` (idempotent; re-points on each call)
void dispose(); // free all GL objects, deregister; idempotent
```

### `FramebufferSpec`

```java
static final int MAX_COLOR_ATTACHMENTS = 8;

static Builder builder();
List<ColorFormat> colorFormats(); // unmodifiable
int colorCount();
DepthMode depthMode();

// Builder
Builder color(ColorFormat format); // add a color attachment, in order (location 0, 1, ...)
Builder depthTexture(); // depth as a sampleable DEPTH_COMPONENT24 texture
Builder depthRenderbuffer(); // depth as a write-only DEPTH_COMPONENT24 renderbuffer
FramebufferSpec build(); // throws if 0 color attachments or more than 8
```

### `ColorFormat`

```java
RGBA8 // 4 bytes/px  - 8-bit LDR color, the default workhorse
RGBA16F // 8 bytes/px  - 16-bit float HDR (bloom, accumulation)
R11G11B10F // 4 bytes/px  - packed float HDR, no alpha
R8 // 1 byte/px   - single channel (masks, scalars)

int glInternalFormat(); int glFormat(); int glType(); int pixelBytes();
```

### `DepthMode`

```java
NONE // no depth attachment
TEXTURE // sampleable depth texture
RENDERBUFFER // write-only depth renderbuffer (cheaper, not sampleable)
```

### `PingPongBuffer`

```java
PingPongBuffer(FramebufferSpec spec); // two screen targets at scale 1.0
PingPongBuffer(float scale, FramebufferSpec spec); // two screen targets at `scale`
void pass(int passes, BiConsumer<Framebuffer, Framebuffer> body); // (read, write) each pass; swaps after each; passes >= 1
Framebuffer read(); // the buffer holding the final result after the last pass
void dispose(); // disposes both internal targets
```

### `FramebufferException`

An unchecked `RuntimeException` thrown on: a `screen` scale of zero or less; a spec with zero or more than eight color attachments; a non-positive allocation size; a GL framebuffer that comes back incomplete; using a target after `dispose()`; `begin()` before allocation; and `pass()` with fewer than one pass.

---

## Status

- All operations run on the render thread and make immediate GL calls. There is no deferred command queue here.
- Live targets are tracked in an internal registry. On `CLIENT_STOPPING`, the registry force-disposes everything still alive, so a forgotten `dispose()` does not leak past the session  - but it still leaks for the rest of the session and across world reloads. Dispose your own targets.
- The systems built on this  - bloom's mip chain, the deferred-light capture, the entity-effect scene snapshot, scene capture  - are good references for real usage. Bloom builds an array of `screen` targets at halving scales; the light pass blits color+depth from main into an RGBA8+depth target and runs one fullscreen pass; the scene snapshot blits color and registers it by name.

---

## See Also

- [Bloom](Bloom). Builds a downsample/upsample mip chain out of HDR screen framebuffers.
- [Deferred Lights](Deferred-Lights). Blits color and depth from main, then lights in a fullscreen pass.
- [Scene Capture](Scene-Capture). Higher-level snapshot of the frame as a named texture.
- [Writing Shaders](Writing-Shaders). GLSL conventions, samplers, and `moj_import` for the passes that read these targets.
