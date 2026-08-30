# Subsurface Scattering

Subsurface scattering is light that enters a surface, bounces around inside the material, and leaves somewhere else. It is what makes skin read red where it is backlit, a leaf glow green when the sun is behind it, and wax look soft instead of plastic.

`Subsurface` gives you a `ShadingModel` you assign to a `ModelMaterial` like any other, plus a global toggle for the screen-space pass that does the spatial half of the work.

```java
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelMaterial;
import com.meekdev.amnetic.client.model.Models;
import com.meekdev.amnetic.client.subsurface.Subsurface;

Model model = Models.load(Identifier.fromNamespaceAndPath("mymod", "models3d/character.glb"));
for (ModelMaterial m : model.materials()) {
    m.setShadingModel(Subsurface.skin());
}
```

Four presets cover the common cases: `skin()`, `wax()`, `foliage()`, and `marble()`.

## Profiles

Anything else is a profile you build yourself. Every parameter is optional and falls back to a neutral default.

```java
ShadingModel jade = Subsurface.profile()
        .strength(0.8f)
        .tint(0.35f, 0.85f, 0.55f)
        .radius(0.02f)
        .thickness(0.6f)
        .nearField(0.5f, 0.004f)
        .power(4.0f)
        .build();
```

| Method | Meaning |
|---|---|
| `strength(v)` | How much of the final colour comes from scattered light. `0` disables the material entirely and it stops costing anything |
| `tint(r, g, b)` | The colour light picks up travelling through the material. Also sets which channels penetrate furthest in the transmission term, which is why skin is `(0.85, 0.35, 0.28)` rather than a flat red |
| `radius(metres)` | `d` in the Burley profile: how far light spreads sideways under the surface. One block is `1.0`, so useful values are small |
| `thickness(metres)` | How far light travels before it is fully absorbed. Drives the backlit transmission term; thin materials like `foliage()` use a small value and glow strongly |
| `nearField(amplitude, metres)` | A short exponential lobe added on top of Burley, for the tight bright response right at a lit edge |
| `power(p)` | Tightness of the transmission lobe. Higher is a narrower rim |

Identical profiles share one shading model, so calling `Subsurface.skin()` in a loop is free after the first call.

## How it works

The effect is split across two places, because the two halves need different things.

**Transmission** is per-fragment. It needs the light loop, so it lives in a snippet spliced into the deferred lighting pass (`shaders/material/subsurface.glsl`). For each light it computes how much energy reaches the viewer after passing *through* the geometry:

```
T(d)    = exp(-sigma_t * d)
L_trans = L_i * T(d) * (1 - exp(-sigma_s * d))
```

`sigma_t` is derived from the tint, so a channel the material transmits well has a low extinction. That is the mechanism behind ears going red against a bright window.

**Diffusion** is spatial. It cannot live in a per-fragment snippet at all, because it has to gather light that entered at *neighbouring* points, so it is its own screen-space pass running right after deferred lighting. The weight is Burley's normalized diffusion profile:

```
S(r) = A * (exp(-r/d) + exp(-r/(3d))) / (8 * pi * d * r)
```

plus the optional near-field lobe `A_n * exp(-r/d_n)`. The profile is normalized, which means the scattering distance does not quietly change the total energy when you tune it.

The gather is separable: a horizontal pass then a vertical one, eleven taps per direction per side, spaced quadratically so most taps land near the centre where the profile is steepest. The world radius is projected to a pixel span at the fragment's depth, so an object keeps a consistent physical scatter distance as it moves toward or away from the camera.

Two things keep light from leaking out of the object. The weight is evaluated against the **true world-space distance** between the centre and each tap, reconstructed from depth, so a tap that lands across a depth discontinuity gets a negligible weight for free. On top of that, taps are rejected outright unless they carry the same material ID.

## Parameters and the ID band

A profile does not compile its own shader. Every profile shares one snippet and reads its numbers from the per-material parameter buffer described in [Shading Models](Shading-Models), bound at binding 1:

| Lane | Contents |
|---|---|
| 0 | `rgb` = tint, `a` = strength |
| 1 | `x` = diffusion radius, `y` = thickness, `z` = near amplitude, `w` = near radius |
| 2 | `x` = transmission lobe power |

Because the ladder collapses identical snippets into one branch, twenty profiles cost one copy of the code and twenty rows of parameters, not twenty shader variants.

## Settings

```java
Subsurface.disable();               // skip the screen-space pass entirely
Subsurface.enable();
Subsurface.isEnabled();
Subsurface.settings().enabled(false);
Subsurface.dispose();               // release the pass buffers and programs
```

The pass is registered at `RenderStage.SCREEN_SPACE` priority 5, ahead of SSAO, so the diffused result is what the later screen-space effects see. It skips itself when no material has a non-zero `strength`, so the cost is genuinely zero until you use it.

## Limitations

* The gather runs on the lit colour buffer, so it diffuses specular along with diffuse lighting. A very sharp highlight on a high-`radius` material will bloom slightly more than it physically should.
* Transmission uses the `thickness` parameter as a constant rather than measuring real geometric thickness from a shadow map, so a thin and a thick part of the same material transmit identically.
* Scattering is screen-space. Light does not carry around a silhouette, and anything occluded is not available to gather from.
* The pass depends on the G-buffer material channel to identify which pixels scatter. With the G-buffer disabled or unpopulated it does nothing.
* Like all shading models, the deferred pass early-outs with zero registered lights, so the transmission half needs at least one `Light` to be visible.

## See Also

* [Shading Models](Shading-Models) for the dispatch mechanism and the parameter buffer
* [Deferred Lights](Deferred-Lights) for the pass the transmission term runs in
* [Screen-Space Effects](Screen-Space-Effects) for the neighbouring passes
* [Models](Models) for assigning the model to a material
