# Shadows

Amnetic deferred lights can cast real shadows. Spot and point lights render per-light depth maps from the geometry immediately around them. The lighting pass samples these maps so surfaces sitting behind an occluder actually go dark. The engine only renders the blocks inside the light's range into the map instead of the whole world. It caches the results and samples everything during the standard deferred lighting pass. A directional sun light works differently: it renders cascaded shadow maps that cover the whole view distance, described in its own section below.

This is a true world-space technique rather than a screen-space trick. Off-screen occluders still cast shadows.

Spot lights, point lights, and one directional sun light cast shadows. Area and tube lights do not cast shadows yet.

## Turning it on

You need to flip a global switch and a per-light flag.

```java
Shadows.enable();
Lights.spot(pos, dir, innerDeg, outerDeg, r, g, b, range, intensity)
      .castsShadow(true);
```

`Shadows` acts as the master switch, with `enable()`, `disable()`, `setEnabled(boolean)`, and `isEnabled()` on it (plus `dispose()`, which Amnetic already calls for you on client shutdown). Calling `castsShadow(true)` opts a specific light into the system. A light only casts a shadow if both are true, it is the right type of light, and it sits within the maximum distance setting from the camera.

You can fade the shadow intensity per light.

```java
light.shadowStrength(0.6f);
```

A value of 0 removes the darkening entirely. A value of 1 gives you a full shadow.

## Settings

All global tuning lives on the `ShadowSettings.defaults()` singleton. Every setter returns the settings object for chaining and clamps its input automatically.

| Setting | Default | Range | Meaning |
| --- | --- | --- | --- |
| `resolution(px)` | `1024` | 256 to 2048 | Power of two size for spot tiles or point cube faces |
| `maxSpotShadows(n)` | `8` | 0 to 16 | Active spot casters allowed |
| `maxPointShadows(n)` | `8` | 0 to 16 | Active point casters allowed |
| `softness(texels)` | `1.5` | 0 to 8 | PCF kernel radius |
| `bias(b)` | `0.0005` | >= 0 | Constant depth bias |
| `normalBias(b)` | `0.05` | >= 0 | World-space push along the surface normal in blocks |
| `maxDistance(blocks)` | `64` | >= 8 | Distance threshold before a light stops baking |
| `fadeStart(frac)` | `0.8` | 0.0 to 1.0 | Fraction of range where the shadow starts fading out |
| `entityShadows(v)` | `true` | boolean | Toggles whether entities cast shadows |
| `entityModels(v)` | `true` | boolean | True uses posed geometry and false uses bounding boxes |
| `pcss(v)` | `true` | boolean | Contact-hardening soft shadows |
| `lightSize(texels)` | `2.5` | 0.1 to 16.0 | PCSS blocker search scale |
| `bakeBudget(n)` | `0` | >= 0 | Maximum dirty maps rebaked per frame where 0 is unlimited |

The engine caps point cube faces at 1024 pixels regardless of your resolution setting; the derived getter `pointFaceSize()` reports the effective face size. Six faces per caster makes the cube array the heaviest VRAM cost. Two more derived values you may bump into: `fadeStartDistance()` returns `maxDistance * fadeStart` in blocks, and the constants `ShadowSettings.SPOT_GRID` (4), `MAX_SPOT` (16), `MAX_POINT` (16), and `MAX_CASCADES` (4) are the hard ceilings behind the clamps in the table.

## What casts shadows

Solid blocks bake their collision shapes as standard AABB boxes.

Cutout blocks like glass panes, iron bars, and leaves bake their textured models using an alpha test. Transparent texels let light pass through perfectly. If a block contains any solid geometry alongside cutout pieces, the engine falls back to the solid AABB box.

Translucent blocks like stained glass use the same alpha testing but also record their color. This produces colored shadows for spot lights. Point shadows remain strictly grayscale.

Entities cast shadows based on their posed model geometry. You can flip `entityModels(false)` to force them to cast standard bounding boxes instead. Entity occluders rebake every single frame at their interpolated render positions.

## Sun cascaded shadows

A directional light can act as the sun and cast cascaded shadow maps over the whole scene. The first enabled directional light with `castsShadow(true)` becomes the sun. Any extra directional lights never cast. Enabling works exactly like the other light types.

```java
Shadows.enable();
Lights.directional(new Vector3f(-0.5f, -0.8f, -0.32f), 1f, 0.96f, 0.9f, 2.5f)
      .castsShadow(true);
```

The engine splits the view distance into up to four cascades, tightest first. Fragments near the camera sample a high detail map and distant fragments fall through to progressively looser ones. Each cascade fits an orthographic projection to its slice of the camera frustum and snaps its center to shadow texels, so edges stay stable instead of shimmering as the camera moves. Nearby blocks, entities, custom models, and instanced meshes all render into the cascade array. Sun maps rebake every frame because terrain streams along with the camera, so there is no stable state to cache on.

The sun behaves differently from every other shadow caster. Vanilla already lights the scene with full daylight before Amnetic runs, so adding sun radiance on top would double-light everything. Instead the sun only carves shadows out of that existing daylight. The light's intensity controls how deep the shadow darkens the surface, not how bright the scene gets. The per-light `shadowStrength(...)` fade works the same way it does for spot and point lights.

Sun shadows fade out smoothly as fragments approach the `sunDistance` limit, over roughly the last 15 percent of that range, instead of cutting off at a hard line.

The sun has its own group of settings on `ShadowSettings.defaults()`.

| Setting | Default | Range | Meaning |
| --- | --- | --- | --- |
| `sunResolution(px)` | `2048` | 512 to 4096 | Power of two size per cascade layer |
| `sunCascades(n)` | `4` | 1 to 4 | Number of cascade layers |
| `sunDistance(blocks)` | `128` | >= 16 | How far from the camera sun shadows reach |
| `sunSplitLambda(l)` | `0.7` | 0.0 to 1.0 | Blends logarithmic and uniform cascade splits |
| `sunCasterExtension(blocks)` | `64` | >= 0 | Extra reach toward the sun so tall geometry outside a slice still casts into it |
| `sunBlockOccluderRadius(blocks)` | `48` | 0 to 96 | Radius around the camera where blocks cast, 0 disables block occluders |

The shared `bias`, `normalBias`, `softness`, `entityShadows`, and `entityModels` settings apply to the sun as well. Sampling uses the same rotated Poisson PCF as the other lights, but PCSS stays spot only, so the sun keeps a fixed penumbra. The default configuration costs 64 MB of VRAM for the cascade array. The editor exposes everything under Renderer then Shadows then Sun cascades.

## Soft shadows

Shadow edges use a rotated 16-tap Poisson PCF. Enabling PCSS forces the penumbra to harden at the contact point and soften as distance increases. The point light path wraps this exact same Poisson kernel around the cube sample direction.

Shadows fade out over the outer portion of a light's range based on the `fadeStart` setting. They die out with the light falloff instead of hitting a hard spherical wall. Because of this falloff, an omnidirectional point light in fog looks like a glowing sphere. If you want hard beam shafts, use a spot light.

## Performance

The system caches depth maps aggressively. A map only rebakes if its occluders move, the light pose changes, or dynamic entities walk into range. A static light with no moving entities nearby costs almost nothing per frame. You can track this overhead in the editor stats under bake time.

The `bakeBudget` limits how many dirty casters can rebake in a single frame. This prevents massive lag spikes by spreading the updates across multiple frames.

Casters sort by distance. When you exceed the active shadow limits, the engine prioritizes the closest lights and drops shadows from the distant ones.

## Editor

You can configure everything live. Open the in-game editor and navigate to Renderer then Shadows. You get the global toggle, all map settings, and a per-light caster list with strength sliders. The stats section tracks bake time, caster counts, and VRAM estimates. The Lights panel also includes the toggle and strength slider for individual lights.

## Limitations

* Area and tube lights do not cast shadows.
* Colored shadows only work on spot lights. Point and sun shadows are strictly grayscale.
* Sun cascades switch with a hard cut at their boundaries. The PCF filter hides most of the seam but there is no blending between cascades.
* Volumetric god-rays skip directional lights, so the sun does not produce light shafts.
* Cutout and translucent occluders work perfectly for blocks. Entities project their geometry but do not support per-texel alpha masking yet.