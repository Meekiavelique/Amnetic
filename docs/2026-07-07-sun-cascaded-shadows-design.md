# Sun cascaded shadow maps (CSM) — design

Date: 2026-07-07. Adds directional (sun) shadows to the existing shadow subsystem, which previously
supported spot (atlas) and point (cube array) lights only.

## Activation

The first enabled `DIRECTIONAL` light with `castsShadow(true)` becomes the sun (extras don't cast).
`Shadows` must be enabled as usual. Typical game wiring:

```java
Shadows.enable();
Lights.directional(new Vector3f(-0.5f, -0.8f, -0.32f), 1f, 0.96f, 0.9f, 2.5f).castsShadow(true);
```

## Architecture

- **`SunShadowCascades`** (`shadow/internal`): `GL_TEXTURE_2D_ARRAY` of `DEPTH_COMPONENT32F`, one layer
  per cascade (max 4, tightest first), single FBO retargeted per layer — mirrors `PointShadowArray`.
- **`ShadowMapPass.bakeSun`**: runs after the spot/point loop, every frame (streamed-terrain casters move
  with the camera, so there is no stable signature to cache on, unlike spot/point bakes).
  - Splits: practical split scheme (`sunSplitLambda` blends log/uniform) over `[0.05, sunDistance]`.
  - Fit: each cascade's ortho is fit to the **bounding sphere** of its camera-frustum slice. Sphere fit
    keeps the radius rotation-stable, so **texel-snapping the center** (done in double precision, in
    absolute world space, on the light's right/up axes) fully kills edge shimmer.
  - Depth range: eye sits `radius + sunCasterExtension` toward the sun; far plane `2*radius + extension`,
    so tall geometry off-slice still casts in.
  - Caster sources, each in its own reference frame (same precision conventions as the spot path):
    | source | space |
    |---|---|
    | block occluders + entities | anchor-relative (int block anchor) |
    | custom models (`ModelRegistry.renderShadow`) | cascade-center-relative |
    | instanced meshes + shader-side sampling | camera-relative (one shared matrix) |
  - Block occluders: ONE collection around the camera (key `SUN_OCCLUDER_ID = -1`), center snapped to an
    8-block grid so the cached mesh survives camera motion; radius `sunBlockOccluderRadius` (0 = off).
    Every cascade draws the same mesh — the ortho clips the rest.
- **Sampling** (`common/shadowmap.glsl`): `shadowRef >= 2000` routes to `amnetic_sampleSun` — first-fit
  cascade selection (tightest whose ±0.97 NDC border contains the fragment), 16-tap rotated-Poisson PCF,
  per-cascade texel-scaled normal bias, `ShadowBias * (1 + cascade)` depth bias. `shadowVisibilityCheap`
  gets a single-tap variant.
- **`deferred.fsh`**: directional lights now fade shadows by camera distance against `SunShadowDistance`
  (the old fade used distance-to-light-position, which is meaningless for directional and would have
  zeroed the sun's shadows out).
- **`DeferredLightingPass`**: binds the cascade array on texture unit 8, uploads `SunViewProj[4]`,
  `SunCascadeRadius[4]`, count/resolution/distance.

## Settings (`ShadowSettings`)

`sunResolution` (2048, 512–4096) · `sunCascades` (4, 1–4) · `sunDistance` (128) · `sunSplitLambda` (0.7)
· `sunCasterExtension` (64) · `sunBlockOccluderRadius` (48, 0 disables blocks). Shared with spot/point:
`bias`, `normalBias`, `softness`, `entityShadows`, `entityModels`. ImGui: Renderer → Shadows → "Sun
cascades".

## Non-goals / known limits

- No sun god-rays (the volumetric loop still skips directional — it sphere-clips against light position).
- No colored/translucent sun shadows (depth-only array; cutout alpha-discard still respected).
- No cascade blending at boundaries (first-fit hard switch; PCF hides most of the seam).
- PCSS is spot-only; the sun uses fixed-radius PCF.
- VRAM at defaults: 2048² × 4 B × 4 cascades = 64 MB.

## Verification status

Compiles (`gradlew build`); assembled `deferred.fsh` (with includes stitched) passes glslangValidator.
Bias defaults, split lambda, and caster extension need in-game visual tuning.
