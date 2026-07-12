# Bloom

Bright light sources and brightly lit regions are often difficult to convey to the viewer as the intensity range of a monitor is limited. One way to distinguish bright light sources on a monitor is by making them glow; the light then bleeds around the light source. This effectively gives the viewer the illusion these light sources or bright regions are intensely bright.

![Image](https://files.catbox.moe/zgtb6t.png)

The whole thing is two pieces.

- **`Bloom`** is the facade. It owns one global settings object and one renderer. Amnetic's [render pipeline](Render-Pipeline) already calls `Bloom.render(ctx)` every frame; all you do is turn it on and change the settings.

- **`BloomSettings`** holds the settings: whether it's on at all, the bright-pass `threshold` and `knee`, whether the emissive-mesh path captures *everything* or *only flagged meshes*, whether it respects depth, how hot the glow is, how many blur levels it builds, and how far down it shrinks the pyramid.

A few things to know up front. Bloom is **off by default**. When it's on, it gathers glow from two sources at once: a **scene bright-pass** that picks up anything brighter than `threshold` (on by default, threshold `0.75`), and a **re-render of instanced meshes** you've marked emissive in the [instanced renderer](Instanced-Rendering). You never register a render call yourself; Amnetic drives bloom from its own pipeline at the tail of world rendering. And if a frame ever throws, bloom logs the error and quietly turns itself off rather than taking world rendering down with it.

---
## How Bloom Works

A simple way to think about bloom is that it happens in three steps: **capture**, **blur**, and **composite**.

### 1. Capture

The first step is creating an image that contains only the things that should glow. Bloom clears an HDR (`RGBA16F`) buffer to transparent black, then fills it from two sources.

**The emissive-mesh path.** If any `WORLD_LAST` instanced mesh is flagged emissive (or `all(true)` is set), those meshes are re-rendered into the buffer. If occlusion is enabled (which it is by default), bloom copies the depth buffer from the main scene and renders with `GL_LEQUAL`, so an emissive object only contributes where it is actually visible. Without this, a glowing object hidden behind a wall could still create a glow on top of the wall.

**The scene bright-pass.** If `threshold` is above zero (it is by default), bloom also copies the main target's color and depth and runs a prefilter over them, adding anything bright enough into the same buffer. The pass uses a soft-knee curve: pixels well above `threshold` contribute fully, pixels just below it fade in gradually over a window controlled by `knee`, and everything darker is discarded. Sky pixels are excluded via depth. When the [G-buffer](Render-Pipeline) is populated, the bright-pass reads the G-buffer's emissive target instead of raw scene color, so vanilla terrain and sky can never bloom no matter how bright they render; without a G-buffer it falls back to thresholding the scene color directly.

Set `threshold(0f)` and the bright-pass is skipped entirely, leaving only the emissive-mesh path.

**The occlusion-query skip.** The capture step runs inside a `GL_ANY_SAMPLES_PASSED` occlusion query. If last frame's query reported that nothing wrote a single glowing pixel, bloom skips the pyramid and composite entirely this frame. A frame with nothing glowing costs almost nothing, and because the check uses last frame's result, a glow appearing after a fully dark frame can arrive one frame late. You'll never notice; your GPU will.

### 2. Blur Pyramid

Doing a huge blur directly on a full-resolution image would be expensive, so bloom uses a mip pyramid instead.

It starts by repeatedly downsampling the capture buffer, cutting the resolution in half at each level. A 13-tap downsample filter is used here (the Jimenez dual-filter that many modern games use). As the image gets smaller, bright pixels naturally spread out, creating a wider and softer glow.

Once the smallest level is reached, bloom works its way back up the pyramid. Each level is upsampled and blended into the next larger one using a 9-tap tent filter with additive blending (`GL_ONE, GL_ONE`).

By the time it reaches the top level again, multiple blur sizes have been combined together. This is what gives bloom its soft layered look instead of looking like a single large blur.

### 3. Composite

The final bloom texture is then added back onto the main framebuffer.

Because each pyramid level contributes additional light, the result is normalized using `2 / levels`. This keeps the overall brightness fairly consistent as more levels are added. Increasing the number of levels mainly makes the bloom wider and softer rather than significantly brighter.

The user-controlled `intensity` value is applied on top of that normalization.

### Implementation Notes

All of these passes are rendered using fullscreen triangles and small GLSL shaders. There is no vertex buffer involved; the vertex shader generates the triangle directly from `gl_VertexID`.

The intermediate textures use the `RGBA16F` format, allowing bloom to accumulate in HDR space without being clamped to the normal 0-1 range during processing.


---

## Turning it on

One thing has to happen: you enable the settings. That's it.

```java
import com.meekdev.amnetic.client.bloom.Bloom;

Bloom.settings().enabled(true); // scene bright-pass + flagged emissive meshes

// or the shorthand, which also blooms every WORLD_LAST mesh:
Bloom.enable(); // enabled(true) + all(true)
```

Amnetic drives bloom itself, in the POST stage of its pipeline, run at the `renderLevel` TAIL. That's after the deferred lighting pass, after translucents, clouds, and weather, after TAA, and just before the color grade, so bloom sees the finished, lit frame. **Do not register `Bloom::render` in a render event yourself.** The pipeline already calls it every frame; a second registration blooms the frame twice.

`Bloom.render(ctx)` is cheap when there's nothing to do. It returns immediately if bloom is disabled, or if there's no emissive geometry *and* the threshold is zero, and the occlusion-query skip covers the frames where sources exist but nothing actually glowed. And it's wrapped in a catch-all: a bad frame logs to the `Amnetic/Bloom` logger and disables bloom rather than crashing your render loop.

> **Heads up:** the emissive-mesh path only ever looks at instanced meshes whose phase is `WORLD_LAST`. Meshes in `BEFORE_ENTITIES` or `AFTER_ENTITIES` are never captured for bloom, no matter how they're marked. (Their HDR output can still bloom through the scene bright-pass if it clears `threshold`.)

## What "emissive" means, and how to mark it

The emissive-mesh path captures a mesh only if you told the instanced renderer that mesh is emissive. You do that when you build the `InstancedMesh`:

```java
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.InstancePhase;

InstancedMesh.builder(MY_SHADER)
    .phase(InstancePhase.WORLD_LAST) // required for bloom to see it
    .emissive() // mark it as a glow source
    .onRender((ctx, batch) -> { /* ... */ })
    .register(Identifier.fromNamespaceAndPath("mymod", "neon_sign"));
```

`emissive()` flags the mesh and sets its strength to `1.0`. `emissive(float strength)` does the same but lets you dial the strength. (Strength is a property the mesh carries; whether your shader uses it is up to your shader.) See [Instanced Rendering](Instanced-Rendering) for the full builder.

With `all(false)` (the default), bloom re-renders exactly these flagged `WORLD_LAST` meshes into the capture buffer. With `all(true)`, it ignores the flag and re-renders *every* `WORLD_LAST` mesh, so the brightness of whatever your shaders output is what blooms, emissive or not.

---


## The knobs

Everything lives on `Bloom.settings()`, and every setter chains, so you can configure it all in one go.

```java
Bloom.settings()
    .enabled(true)
    .threshold(0.75f) // scene brightness that starts to bloom (default)
    .knee(0.5f) // soft roll-in below the threshold (default)
    .all(false) // mesh path: only emissive geometry (default)
    .occlude(true) // respect depth; don't bloom through walls (default)
    .intensity(2.0f) // how hot the glow reads
    .levels(8) // more mips = wider, smoother falloff
    .scale(0.25f); // quarter-res pyramid: faster, softer
```

**`enabled`**

`false` by default. While it's off, `render()` is a no-op, so there's no cost to leaving bloom configured when you're not using it.

**`threshold`**

The brightness where the scene bright-pass starts picking pixels up. Defaults to `0.75`, clamped to `>= 0`. Lower it and more of the scene glows; raise it and only truly hot pixels bloom. Set it to `0` to turn the bright-pass off entirely, leaving only the emissive-mesh path. Values are HDR, so a threshold above `1.0` is meaningful if your shaders output overbright color.

**`knee`**

How softly the bright-pass rolls in below the threshold. Clamped to `[0, 1]`, defaults to `0.5`. At `0` the threshold is a hard cut, which can shimmer on pixels teetering at the edge. Higher values fade the contribution in over a wider window below the threshold, which reads smoother.

**`all`**

Controls what the emissive-mesh path captures. Leave it `false` and only meshes you've explicitly marked as emissive are re-rendered. Set it to `true` and the pass captures every `WORLD_LAST` instanced mesh, so anything those shaders render bright will bleed light.

Pick `false` when you want surgical control over which meshes glow. Pick `true` when your mesh shaders already output HDR values and you want all of them to naturally bloom. Note that `Bloom.enable()` flips this to `true` automatically.

**`occlude`**

Depth awareness for the emissive-mesh path. When `true` (the default), re-rendered meshes only contribute where they're actually visible, so glowing geometry behind a wall won't leak through it. When `false`, every emissive surface blooms regardless of what's in front of it, slightly cheaper and useful if you want an x-ray glow effect. (The scene bright-pass reads the finished frame, so it's inherently occlusion-correct either way.)

One thing to keep in mind: changing `occlude` rebuilds the buffer chain, because it changes whether the capture buffer carries a depth texture. Don't toggle it every frame.

**`intensity`**

How strong the glow looks when it's composited back over the scene. Defaults to `1.0`. It multiplies on top of the internal `2 / levels` normalization, so it means roughly the same thing regardless of how many levels you've picked. Push past `1.0` for a hot, blown-out look; pull toward `0` to make it subtle.

**`levels`**

How many mips the blur pyramid has. Clamped to `[2, 8]`, defaults to `6`. More levels means the glow reaches further and falls off more smoothly. Fewer levels keeps it tight around the source.

Because of the normalization, adding levels widens and softens the glow rather than brightening it. Each level is another downsample + upsample pass, so more levels does cost more. Like `occlude` and `scale`, changing this rebuilds the chain.

**`scale`**

The resolution of the base of the pyramid, relative to screen resolution. Clamped to `[0.05, 1.0]`, defaults to `0.5` (half res). Each level below the base is half again.

`1.0` starts at full resolution (sharpest, most expensive). `0.5` at half. `0.25` at quarter, which is much cheaper and only marginally softer. Since bloom is blurry by definition, half or quarter res is almost always the right call. You rarely see the difference, and you save a lot of fill rate.

**Shorthands**

`Bloom.enable()` is shorthand for `enabled(true).all(true)`. `Bloom.disable()` is shorthand for `enabled(false)`.

---

## Where it sits in the frame

Bloom runs in the POST stage of Amnetic's pipeline (order 10), driven from the `renderLevel` TAIL. By then the [deferred lights](Deferred-Lights) have finished (they run earlier, in the LIGHTING stage at `END_MAIN`), translucents, clouds, and weather have drawn, and TAA has resolved. Bloom captures its sources, blurs, and composites straight back onto the main target; the color grade runs right after it.

All intermediate buffers are `RGBA16F`, so the pyramid accumulates in HDR (see [Framebuffers](Framebuffers)). The capture buffers are allocated at `scale` resolution, the same as the base of the pyramid; capturing at full screen resolution would be wasted work since the chain downsamples immediately. The chain is built lazily on the first frame that needs it and reused after that, and only rebuilt when `scale`, `levels`, or `occlude` change.
---

## Worked examples

### Make my emissive models glow

You have an instanced mesh for a glowing rune. You want it, and only it, to bloom. Nothing else in the world.

```java
// when you build the mesh: mark it emissive, put it in WORLD_LAST
InstancedMesh.builder(RUNE_SHADER)
    .phase(InstancePhase.WORLD_LAST)
    .emissive()
    .onRender((ctx, batch) -> batch.write(activeRunes))
    .register(Identifier.fromNamespaceAndPath("mymod", "rune"));

// at client init: turn bloom on (emissive-only mesh capture is the default)
Bloom.settings()
    .enabled(true)
    .all(false) // mesh path captures only emissive meshes -- i.e. the rune
    .threshold(0f) // kill the scene bright-pass so nothing else glows
    .intensity(1.4f);
```

That's it. The rune's bright output bleeds a soft halo; the rest of the world is untouched. If runes ever appear to glow through walls, check that `occlude` is at its default `true`, which it already is unless you changed it.

### A tuned glow

You're shooting a cinematic and want a wide, dreamy, hot bloom over the whole scene, driven by scene brightness rather than emissive flags.

```java
Bloom.settings()
    .enabled(true)
    .all(true) // every WORLD_LAST mesh feeds the capture too
    .threshold(0.6f) // let more of the scene cross into glow
    .knee(0.7f) // and roll it in softly
    .intensity(2.2f) // hot
    .levels(8) // widest, smoothest falloff
    .scale(0.5f); // half-res base is plenty for a soft look
```

Because intensity is normalized against level count, bumping `levels` to the max gives you the big soft halo without having to compensate the brightness separately. `intensity(2.2f)` reads the same at 8 levels as it would at 6.

If you want the bloom to swell into a shot, drive `intensity` with a [tween](Tweens):

```java
Animations.tween(0.6f, 2.2f, 1.5f)
    .ease(Easing.SINE_IN_OUT)
    .onUpdate(v -> Bloom.settings().intensity(v))
    .start();
```

---

## Performance and pitfalls

**Cost scales with `scale` x `levels` x screen resolution.** Each level is a downsample and an upsample full-screen pass. Doubling resolution via `scale` quadruples the base fill; adding a level adds two more passes. Half-res (`0.5`) with 6 levels is a sane default. Drop to `0.25` if you're fill-bound, and only go to `scale(1.0)` if you genuinely need a crisp base.

**`all(true)` re-renders every `WORLD_LAST` mesh** into the capture buffer, so it's a second draw of all of them. If you have a lot of instanced geometry, emissive-only mode is much cheaper since it only re-draws the flagged meshes.

**Don't toggle `scale`, `levels`, or `occlude` per frame.** Each change disposes and rebuilds the whole buffer chain. `intensity`, `threshold`, `knee`, `enabled`, and `all` are free to change every frame; the others are not.

**Expected a glow and got none?** With `threshold(0)`, bloom needs at least one `WORLD_LAST` mesh flagged emissive or it early-outs. With the default threshold, check that whatever should glow actually renders brighter than `threshold`; if the G-buffer is populated, the bright-pass reads the G-buffer emissive target, so surfaces that don't write emissive there won't bloom from brightness alone.

**Two sources, one buffer.** The scene bright-pass reads the finished frame; the mesh path re-renders emitters. If a mesh both writes G-buffer emissive and is flagged `emissive()`, it feeds the capture from both sides, which reads hotter than you might expect. Tune with `intensity` or drop one source.

**The occlusion-query skip lags one frame.** Whether the pyramid runs is decided by *last* frame's query. A glow igniting from a completely dark frame can appear one frame late, and the last frame after everything stops glowing still pays for one pyramid. Harmless, but don't be surprised by it in captures.

---

## Reference

### `Bloom`

```java
BloomSettings settings(); // the one global settings instance
void enable(); // enabled(true) + all(true)
void disable(); // enabled(false)
void render(LevelRenderContext ctx); // called by Amnetic's pipeline (POST, order 10); don't call or register it yourself
```

`render` no-ops when disabled, or when the threshold is zero and there's no emissive geometry. When sources exist but nothing glowed last frame, an occlusion query skips the pyramid and composite. It swallows and logs its own exceptions (logger `Amnetic/Bloom`) and disables bloom on failure so it can't break world rendering.

### `BloomSettings`

All setters return `this` and chain.

```java
BloomSettings enabled(boolean v);   boolean isEnabled(); // master switch;             default false
BloomSettings threshold(float v);   float threshold(); // bright-pass cutoff,        default 0.75
                                                           //   clamp >= 0; 0 = off
BloomSettings knee(float v);        float knee(); // soft roll-in below         default 0.5
                                                           //   threshold, clamp [0, 1]
BloomSettings all(boolean v);       boolean isAll(); // mesh path: all meshes vs   default false
                                                           //   emissive-only
BloomSettings occlude(boolean v);   boolean isOcclude(); // depth-aware; no bloom      default true
                                                           //   through walls
BloomSettings intensity(float v);   float intensity(); // glow strength;             default 1.0
BloomSettings levels(int v);        int levels(); // mip count, clamp [2, 8];   default 6
BloomSettings scale(float v);       float scale(); // pyramid base scale,        default 0.5
                                                           //   clamp [0.05, 1.0]
```

Changing `scale`, `levels`, or `occlude` rebuilds the buffer chain (capture buffers and pyramid alike); the others are free per frame. The chain is built from `RGBA16F` framebuffers at `scale` resolution, so bloom accumulates in HDR before compositing.

### Marking geometry emissive (from `InstancedMesh.Builder`)

```java
Builder<T> emissive(); // flag emissive, strength 1.0
Builder<T> emissive(float strength); // flag emissive with a strength
```

Only `WORLD_LAST` meshes are ever considered for the mesh path. See [Instanced Rendering](Instanced-Rendering).

---

## See Also

- [Framebuffers](Framebuffers). The HDR (`RGBA16F`) buffers and the mip pyramid bloom builds on.
- [Instanced Rendering](Instanced-Rendering). Where you mark a mesh emissive and pick its phase.
- [Deferred Lights](Deferred-Lights). Runs earlier, in the LIGHTING stage at `END_MAIN`; bloom picks up its output through the bright-pass.
