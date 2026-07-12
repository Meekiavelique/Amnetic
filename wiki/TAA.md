# TAA

Temporal anti-aliasing smooths edges and stops specular shimmer by spreading the work of anti-aliasing across time. Every frame the world is rendered with a tiny sub-pixel camera offset, and a resolve pass blends the result with an accumulated history of previous frames. Over a handful of frames each pixel effectively gets supersampled without ever paying for supersampling. Because temporal accumulation softens the image slightly, a contrast-adaptive sharpening (CAS) pass runs at the end of the frame to bring back edge crispness.

Unlike bloom, you don't have to wire anything up. TAA is **on by default** and its passes are registered in Amnetic's own [render pipeline](Render-Pipeline). The only things you'd normally touch are the settings:

```java
import com.meekdev.amnetic.client.taa.Taa;

Taa.settings()
    .feedback(0.9f) // how much history each frame keeps
    .sharpness(0.4f); // CAS strength; 0 disables the sharpen pass

Taa.disable(); // or turn the whole thing off
Taa.enable();
```

---

## How it works

TAA is three cooperating pieces: a **jitter** applied to the projection matrix, a **resolve** pass that reprojects and blends history, and a **sharpen** pass that runs last.

### 1. Jitter

Each frame the projection matrix gets translated by a sub-pixel offset in clip space. The offsets come from a Halton(2,3) low-discrepancy sequence with an 8-frame cycle, centered around zero and scaled to exactly one pixel of amplitude at the current framebuffer size.

The jitter is premultiplied onto the projection at the single place vanilla uploads the world projection (a mixin on `GameRenderer`), so terrain, entities, Amnetic's instanced geometry, and the `FrameView` capture all see the same offset. Nothing renders "unjittered by accident" and slides against the rest of the world. [Scene captures](Scene-Capture) are deliberately left unjittered, since they don't go through the TAA resolve.

The jitter only applies while TAA is enabled (and never under Iris, see [Limitations](#limitations)). When it's off, the offset is reset to zero and the projection is untouched.

### 2. Resolve

The resolve is a fullscreen pass that runs first in the `POST` stage, before bloom and color grading, so the accumulation happens on the raw lit scene. It copies the main target's color and depth into a capture buffer, then blends it with the previous resolved frame into one of two ping-ponged `RGBA16F` history buffers, and blits the result back onto the main target.

Finding "where this pixel was last frame" is done by **camera-only reprojection**: the pass reconstructs the pixel's camera-relative world position from the depth buffer, offsets it by how far the eye moved, and projects it through the previous frame's view-projection matrix. Both matrices are unjittered before use; the jitter is the sampling signal inside the frame, not camera motion. This is exact for everything static, which is most of a Minecraft frame.

Several safeguards keep the history honest:

- The reprojected history sample is **clipped against a 3x3 neighborhood variance box** (one sigma, in YCoCg space) around the current pixel. If the history color falls outside what the current neighborhood could plausibly contain, it's pulled back in. This is what handles moving objects, since there are no per-object motion vectors. Clipping toward the box center (rather than clamping per channel) preserves hue.
- History is sampled with a **Catmull-Rom filter** (five bilinear fetches) instead of plain bilinear, which would re-blur the accumulation every frame and make the image go soft in motion.
- The blend is **inverse-luminance weighted**, so a single very bright frame (a firefly) can't dominate the accumulation.
- Feedback **fades with screen-space velocity**: a pixel that moved far across the screen keeps less history (up to half as much at 80 pixels of motion), reducing smear during fast camera pans.
- The history is **invalidated on camera cuts**. If the eye jumps more than 16 blocks in one frame (teleport, dimension change), the resolve outputs the current frame as-is instead of smearing the old view over the new one. The same happens when TAA is re-enabled after being off, and for any pixel whose reprojection lands off screen or behind the camera.

### 3. Sharpen

The CAS pass runs last in the `POST` stage, after bloom and color grading, so the whole composited frame gets sharpened. It's a simplified AMD FidelityFX CAS: a cross-shaped 5-tap filter whose sharpening amount adapts per pixel to the local contrast, so edges that are already crisp (or clipped) don't ring or halo. The `sharpness` setting picks the filter peak; at `0` the pass is skipped entirely.

---

## The knobs

Everything lives on `Taa.settings()`, and every setter chains.

| Setting     | Default | Range       | Meaning                                                                 |
|-------------|---------|-------------|-------------------------------------------------------------------------|
| `enabled`   | `true`  | -          | Master switch for jitter, resolve, and sharpen together.                 |
| `feedback`  | `0.9`   | `0 - 0.98`  | Fraction of history kept each frame. Higher converges smoother but trails longer; `0` means no accumulation at all. |
| `sharpness` | `0.4`   | `0 - 1`     | CAS strength. `0` skips the sharpen pass.                                |

`Taa.enable()` and `Taa.disable()` are shorthand for `settings().enabled(true/false)`. Turning TAA off also drops the accumulated history, so re-enabling starts fresh rather than reprojecting stale frames.

---

## Editor

The live controls are in the editor under **Renderer > TAA**: the enable checkbox, the history feedback slider, and the CAS sharpness slider. All three take effect immediately, so it's the easiest way to feel out how much feedback and sharpening a scene wants.

---

## Where it sits in the frame

Both passes are registered in the default pipeline (see [Render Pipeline](Render-Pipeline)):

```
POST  5   TAA           resolve, before bloom
POST 10   Bloom
POST 20   Color Grade
POST 30   CAS Sharpen   after everything else has composited
```

The resolve intentionally runs before bloom so the glow isn't folded into the temporal history, and the sharpen intentionally runs last so it crisps the final graded image.

Like every Amnetic screen pass, both are wrapped in a failure guard: a bad frame logs to `Amnetic/TAA` (or `Amnetic/CAS`) and backs the pass off instead of taking world rendering down.

---

## Limitations

**No per-object motion vectors.** Reprojection accounts for camera motion only. Moving entities, animated instanced meshes, and particles are handled solely by the neighborhood clip, which prevents visible ghosting but means fast movers get less temporal smoothing and can look slightly softer or noisier than static geometry.

**Disabled under Iris.** Every Amnetic screen pass skips itself when Iris is loaded, and TAA follows suit: with no resolve pass to integrate it, the projection jitter would just make the world shimmer, so `Taa.jitterActive()` returns `false` under Iris and the projection is left alone. There is no way to run Amnetic's TAA alongside an Iris shaderpack.

**Scene captures are unjittered.** [Scene captures](Scene-Capture) and planar reflections render without the jitter and never pass through the resolve, so they don't get anti-aliased by TAA.

**Memory cost is fixed.** The system keeps two full-resolution `RGBA16F` history buffers plus an `RGBA8` capture buffer with a depth texture, allocated lazily on the first enabled frame and kept until `Taa.dispose()`.

---

## Reference

### `Taa`

```java
TaaSettings settings(); // the one global settings instance
boolean jitterActive(); // enabled AND not running under Iris
void enable(); // settings().enabled(true)
void disable(); // settings().enabled(false)
void render(); // resolve pass; pre-registered at POST 5
void renderSharpen(); // CAS pass; pre-registered at POST 30
void dispose(); // free all buffers and programs
```

You don't normally call `render()` or `renderSharpen()` yourself; Amnetic registers both in the default pipeline.

### `TaaSettings`

All setters return `this` and chain.

```java
TaaSettings enabled(boolean v);   boolean isEnabled(); // master switch;           default true
TaaSettings feedback(float v);    float feedback(); // history weight,          default 0.9
                                                        //   clamp [0, 0.98]
TaaSettings sharpness(float v);   float sharpness(); // CAS strength,            default 0.4
                                                        //   clamp [0, 1]; 0 skips
```

---

## See Also

- [Render Pipeline](Render-Pipeline). Where the TAA and CAS passes are registered, and how to slot your own around them.
- [Bloom](Bloom). Runs between the resolve and the sharpen in the same `POST` stage.
- [Framebuffers](Framebuffers). The `RGBA16F` history and capture buffers TAA is built on.
