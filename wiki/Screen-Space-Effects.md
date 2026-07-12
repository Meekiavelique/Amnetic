# Screen-Space Effects

SSAO, SSGI, and SSR are three screen-space passes that read the gbuffer for depth and normals before compositing themselves over the lit scene. They run during the surface stage at `END_MAIN`. This puts them underneath translucent geometry. Check the Deferred Lights documentation if you need a refresher on the two-stage render order.

If the gbuffer normal data is missing, all three passes automatically reconstruct surface normals directly from the depth buffer. They work perfectly fine completely standalone.

## Ambient Occlusion

SSAO grounds objects in the world by darkening contact crevices. The shader samples a hemisphere around each pixel to estimate occlusion. It writes that data to an internal buffer, blurs it, and multiplies the result against the scene.

The hemisphere uses 16 samples, rotated per pixel by a 4x4 dither pattern, and a 5x5 bilateral blur averages the dither out without smearing across silhouettes. Temporal accumulation is on by default on top of that. It acts like a lightweight TAA pass, reprojecting the previous frame to clean up the remaining artifacts, and it also advances the dither rotation every frame so the history integrates fresh samples.

```java
Ssao.enable(); // disable() and isEnabled() also exist
SsaoSettings s = Ssao.settings();
s.radius(0.8f).intensity(1.2f).power(1.5f);
```

The effect defaults to off. You can tweak it live in the editor under Renderer > SSAO. Calling `Ssao.settings()` gives you the live configuration object.

The `radius` sets the sample hemisphere size in blocks. It defaults to 0.8.
The `intensity` scales the absolute strength of the shadow. It defaults to 1.2.
The `bias` pushes the depth check outward to prevent flat surfaces from occluding themselves. The default is 0.025.
The `power` applies a contrast curve to the final occlusion factor. It defaults to 1.5.
The `scale` drops the internal render resolution. It defaults to 0.5 and clamps to the 0.25 to 1.0 range. Lower values run much faster but generate more noise.
The `temporal` boolean toggles the history reprojection. It defaults to true.
The `feedback` value sets the history weight when temporal accumulation is running. It defaults to 0.9 and maxes out at 0.95.

## Global Illumination

SSGI calculates a single bounce of indirect light. It gathers color from the lit scene buffer over a hemisphere. The shader checks depth values and back-facing normals so light does not bleed through solid walls. It blurs the gathered color and adds it back to the scene.

```java
Ssgi.enable();
Ssgi.settings().radius(2f).intensity(1f);
```

SSGI also defaults to off. You will find it under Renderer > SSGI in the editor.

The `radius` dictates the gather distance in blocks. It defaults to 2.0.
The `intensity` scales the indirect light contribution. It defaults to 1.0.
The `scale` lowers the render resolution exactly like the SSAO setting. It defaults to 0.75 and clamps to the 0.25 to 1.0 range.
The `historyBlend` sets the exponential moving-average weight given to reprojected history each frame. It defaults to 0.9 and clamps to the 0.0 to 0.98 range. Zero means no temporal accumulation; values near the top are very stable but slow to react.
The `maxHistoryFrames` caps how many frames the temporal ramp-up takes to reach `historyBlend` after a disocclusion or reset, so freshly revealed pixels do not look under-converged forever. It defaults to 16.

Screen-space global illumination is inherently noisy. If your scene looks grainy, you need to adjust the config. Shrinking the radius or dropping the intensity will hide most artifacts. You can also just raise the render scale back toward 1.0 to brute-force a cleaner image.

## Reflections

SSR ray-marches the depth buffer to find what each surface reflects. When the gbuffer is populated the shader reads per-pixel roughness and F0 from the material target, so Amnetic geometry reflects according to its actual material; rough surfaces reflect weakly and mirrors reflect hard. Pixels without gbuffer data (vanilla terrain, entities) fall back to a depth-reconstructed normal, a conservative matte roughness, and the global `reflectivity` value as their F0, which keeps vanilla silhouettes from turning into full-strength sky mirrors at grazing angles.

The march itself uses distance-adaptive steps: short near the surface where precision matters, growing toward `maxDistance`, with a binary search refining the exact hit point so the coarse steps cost no accuracy. Rays that miss, leave the screen, or overshoot `maxDistance` fall back to an analytic sky gradient instead of hard-cutting, and hits near the screen edge blend toward that same fallback over the `edgeFade` band. A Fresnel term shapes the final strength, and the result alpha-blends over the scene.

The ray origin jitters every frame and temporal accumulation (on by default) reprojects the previous frame's result to denoise it, exactly like the SSAO history pass.

```java
Ssr.enable(); // disable() and settings() also exist
Ssr.settings().maxSteps(64).maxDistance(48f).reflectivity(0.1f);
```

SSR defaults to off. There is no editor inspector for it yet; configure it in code (or through a [Quality](Quality) preset, which sets a coherent batch of these knobs for you).

The `intensity` scales the final reflection strength. It defaults to 1.0.
The `maxSteps` caps the ray-march iterations per pixel. It defaults to 32 and clamps to the 1 to 256 range. More steps reach further and miss less, and cost linearly more.
The `maxDistance` limits how far a ray travels in blocks. It defaults to 32.
The `thickness` is the depth tolerance in blocks when deciding whether the ray actually hit a surface or overshot into empty space behind thin geometry. It defaults to 1.0.
The `edgeFade` sets the screen-border band, as a fraction of the screen, over which hits fade into the sky fallback. It defaults to 0.1 and clamps to 0.5.
The `reflectivity` is the fallback F0 for pixels without gbuffer material data. It defaults to 0.04, which is the physical value for common dielectrics; raise it to make vanilla surfaces noticeably reflective.
The `resolution` scales the internal ray-march buffer like the SSAO and SSGI `scale`. It defaults to 1.0 (full resolution) and clamps to the 0.1 to 1.0 range.
The `temporal` boolean toggles the history reprojection. It defaults to true.
The `feedback` sets the history weight, defaulting to 0.85 with a maximum of 0.98.
The `stride` knob exists and is accepted (default 0.5), but the current ray-march shader derives its step length from `maxDistance` and `maxSteps` instead, so changing it has no visible effect right now.

Reflections can only show what is on screen. Anything occluded or behind the camera cannot appear in a reflection; the sky fallback hides most of the resulting artifacts, but expect reflections of off-screen geometry to dissolve into sky as you look away.
