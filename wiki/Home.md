<p align="center">
  <img src="https://files.catbox.moe/jdrud4.png" alt="Java" />
</p>
<p align="center">
  <a href="https://github.com/Meekiavelique/Amnetic/releases">
    <img src="https://img.shields.io/github/v/release/Meekiavelique/Amnetic?style=for-the-badge&color=2ea44f&label=LATEST%20RELEASE" alt="Latest Release" />
  </a>
  <img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Running%20on-Fabric-2C2C2C?style=for-the-badge&logo=openjdk&logoColor=white" alt="Running on Fabric" />
  <a href=" "><img src="https://img.shields.io/badge/Wiki-Documentation-4A90E2?style=for-the-badge&logo=gitbook&logoColor=white" alt="Wiki" /></a>
  <a href="https://discord.gg/avSH2JTfef"><img src="https://img.shields.io/badge/Discord-online-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord" /></a>
</p>

<p align="center">
A Fabric rendering utility library for Minecraft 26.1.2 
</p>

## Installation

Amnetic is a **standalone mod**: install it as a separate mod (e.g. from Modrinth)
alongside any mod that uses it. **Do not bundle it (Jar-in-Jar) inside another mod.**

A single shared install is supportable, whereas many bundled copies are not  - and when
several mods each ship their own copy, Fabric loads one and shadows the rest, causing
version mismatches and compatibility conflicts. Amnetic logs a warning if it detects it
was loaded as a nested jar.

Developers should depend on it with `modImplementation` (not `include`) and add an
`"amnetic"` entry to their `fabric.mod.json` `depends`. See the README and
[Getting Started](Getting-Started) for the build snippet.

---

## Current scope

**Post-processing effects**

Register fullscreen post-processing effects using JSON pipelines and shaders. Effects can be enabled conditionally, smoothly faded in or out, configured with dynamic uniform values from the game state, and applied at three points in the frame: right after the world renders, before the GUI, or after all rendering including the GUI.

**Compute shaders**

Run general-purpose GPU work from the client: load `.comp` programs, dispatch work groups, and exchange bulk data through shader storage buffers and textures, with a capability probe for graceful fallback.

**Particles**

A CPU-simulated, GPU-billboarded particle system that composites on the deferred renderer. You supply the fragment shader and texture; the library handles lifecycle, motion, and camera-facing geometry, plus a scene-depth texture for soft particles.

**Instanced rendering**

Draw one mesh thousands of times in a single draw call with per-instance transforms and colors. Built-in shaders cover transform, transform+color, and textured-billboard cases; custom layouts and shaders are supported.

**World meshes**

A generic world-space mesh render type for custom geometry (fog walls, force fields, beams) with your own shaders, two material textures, and scene-depth access.

**Camera**

Read-only camera queries (world↔screen projection, frustum tests, ray-picking), additive camera effects (shake, impulse/kick, FOV punch, spring) with a per-frame modifier hook for your own motion, and a cinematic director that takes over the camera and blends back.

**Animation**

A small tween and timeline engine for animating any value over time, with easing curves, delay, repeat and yoyo, and lifecycle callbacks. Built-in interpolators cover floats, vectors, colors, angles, and orientations, and you can add your own. The camera effects and director are built on it.

**Framebuffers**

Off-screen render targets you own: fixed or window-tracking, multiple color attachments, optional depth texture, blits to and from the main frame, and a ping-pong pair for multi-pass effects. The foundation the bloom, lights, scene-capture, and entity-effect systems sit on.

**Bloom**

An emissive/bright-pixel glow pass over the deferred renderer, with a configurable mip pyramid, intensity, downsample scale, and optional depth occlusion.

**Deferred lights**

Dynamic point, spot, directional, area, and tube lights with color (or temperature), range, falloff curves, and a global budget with frustum culling and distance fade. Lit in screen space from depth and normals. Per-light extras: cookies/gobos, IES-style angular profiles, and volumetric god-ray strength.

**Shadows**

Spot and point lights cast real world shadows via per-light depth maps (perspective atlas / cube array) baked from nearby blocks, cutout/translucent geometry (coloured shadows from stained glass), and entities. Soft Poisson PCF with PCSS contact-hardening, distance fade, nearest-first LOD, and a static-skip so idle lights cost nothing. A directional sun light gets cascaded shadow maps: up to four texel-snapped cascades covering the view distance that carve shadows out of vanilla daylight (intensity sets shadow depth, not brightness), with a smooth fade at the far edge. See [Shadows](Shadows).

**Screen-space AO & GI**

[SSAO](Screen-Space-Effects) (with TAA-lite temporal denoise) grounds objects in contact crevices; [SSGI](Screen-Space-Effects) adds one bounce of indirect colour bleed. Both read the gbuffer and composite over the lit scene.

**Temporal anti-aliasing**

Full TAA over the frame: sub-pixel Halton jitter on the world projection, a history reprojection resolve that accumulates samples over time, and an optional CAS sharpen pass to win back the softness TAA introduces. See [TAA](TAA).

**Decals**

Projected box decals: give `Decals` a texture, a center, and a surface normal, and the box projects the texture onto whatever geometry it intersects. A decal can also write the gbuffer (normal/roughness) so the deferred lights relight the surface underneath it. See [Decals](Decals).

**Custom shading models**

Register a GLSL snippet as a `ShadingModel` and assign it to a model material. The snippet is baked into the deferred lighting shader as a dispatch case, so your geometry responds to Amnetic lights with a fully custom BRDF instead of the default Cook-Torrance path. See [Shading Models](Shading-Models).

**Subsurface scattering**

Light that enters a surface, scatters inside it, and leaves somewhere else: red backlit skin, glowing leaves, soft wax. A per-fragment transmission term in the deferred pass plus a screen-space Burley diffusion gather, driven by tunable profiles with `skin()`, `wax()`, `foliage()`, and `marble()` presets. See [Subsurface Scattering](Subsurface-Scattering).

**Light styles**

Register a GLSL snippet against a `Light` to customise the light itself rather than the surface: displace where it appears to come from, scale or tint its contribution, or mask it to a region. See [Light Styles](Light-Styles).

**Surface UI**

A retained-mode UI toolkit drawn through Amnetic's renderer: HUD overlays, modal screens, and panels in the world. SDF text with per-glyph effects, shader materials with blur-behind, spring-animated layout, a full widget catalogue, and a fine-grained reactive core of signals, computed values, and effects. See [Surface UI](Surface-UI).

**Quality presets**

One-call presets (`Quality.off()`, `low()`, `balanced()`, `ultra()`) that tune SSAO, SSGI, SSR, and bloom together to trade performance for fidelity. Each system stays fully configurable through its own settings afterwards; a preset is just a starting point. See [Quality](Quality).

**Volumetric god-rays**

Single-scattering light shafts for point/spot lights with a Henyey-Greenstein phase, density, and optional shadow-map occlusion so beams are carved by geometry. Configured on `LightSettings` and per-light.

**Particle editor**

An in-game tool to author particle effects (material, lifetime curves/gradient, affectors, collider), preview them live, and save/load as JSON. Part of the in-game [Editor](Editor).

**Color grading**

A final-frame grading pass: exposure, contrast, saturation, brightness, temperature/tint, gamma, and an optional 3D LUT with intensity blending. See [Color Grading](Color-Grading).

**In-game editor**

An in-game inspector overlay (`AmneticEditor`) for tuning the rendering systems live, with gizmos for world-placed handles and an API for registering your own `Inspector` panels. See [Editor](Editor).

**Models**

Load glTF 2.0 and OBJ models and render them through the instanced pipeline, with PBR metallic-roughness materials, so they are deferred-lit. A clip-based animation player (`Animator`) is included, with GPU geometry skinning for rigged models. Parsed models are cached on disk in a compact `.ammesh` binary format for fast reloads, opt-in LOD chains are generated at load time, and models cast into the shadow maps.

**Scene capture**

Render the world from a virtual second camera into an off-screen texture each frame, plus a high-level planar-reflection helper for mirrors (reflected camera, oblique clip, reflective surface).

**Entity effects**

Run a custom vertex/fragment shader over a living entity's body with your own uniforms and samplers, or just swap an entity's texture through the vanilla shader.

**Mesh tap**

Read the posed vertices (positions, UVs, normals) an entity renders each frame, for driving particles or custom geometry off a live model.

## Planned scope

The following areas are planned for future releases. None of them are available yet.

- FrameGraph pass injection

---

## Navigation

| Page | Description |
|---|---|
| [Getting Started](Getting-Started) | How to add Amnetic to your mod and register your first effect |
| [Render Pipeline](Render-Pipeline) | `Pipeline`, `RenderStage`s, `RenderPass`/`FrameContext`, and per-layer (`RenderLayer`/`LayerPass`) isolation |
| [Post-Processing](Post-Processing) | Full reference for `PostEffects`, `PostEffectHandle`, `RenderPhase`, priority, and the fade system |
| [Uniforms](Uniforms) | `UniformSuppliers` reference and an explanation of how uniform blocks work |
| [Writing Shaders](Writing-Shaders) | JSON pipeline format, GLSL conventions, and a complete worked example |
| [Compute Shaders](Compute-Shaders) | `ComputeShader`, `ShaderStorageBuffer`, `ComputeTexture`, and the capability probe |
| [Particles](Particles) | The `Particles` facade, the billboard shader contract, and `SceneDepth` soft particles |
| [Instanced Rendering](Instanced-Rendering) | `InstancedMesh`, built-in shaders, layouts, `MeshData`, render state, and phases |
| [Camera](Camera) | `AmneticCamera` queries, `CameraEffects` + `CameraModifier`, and the `CameraDirector` |
| [Tweens](Tweens) | The `Animations` engine, `Tween` and `Timeline`, easing curves, and interpolators |
| [Mesh Pipeline](Mesh-Pipeline) | Generic world-space mesh render type for custom geometry |
| [Framebuffers](Framebuffers) | `Framebuffer`, `Framebuffers`, `FramebufferSpec`, formats, and `PingPongBuffer` |
| [Bloom](Bloom) | `Bloom` facade and `BloomSettings` for the emissive glow pass |
| [Deferred Lights](Deferred-Lights) | `Lights` factory, `Light`, types, falloff curves, and `LightSettings` |
| [Shadows](Shadows) | Per-light shadow maps, coloured shadows, PCSS softening, and cascaded sun shadows |
| [Screen-Space Effects](Screen-Space-Effects) | SSAO, SSGI, and SSR over the gbuffer, with their settings |
| [TAA](TAA) | `Taa` and `TaaSettings` for temporal anti-aliasing and the CAS sharpen pass |
| [Color Grading](Color-Grading) | `ColorGrade` and `ColorGradeSettings` for the final-frame grading post pass |
| [Decals](Decals) | `Decals` factory and `Decal` handles for projected box decals and gbuffer relighting |
| [Shading Models](Shading-Models) | `ShadingModel` lighting bases, fragment and vertex GLSL snippets for deferred-lit materials |
| [Subsurface Scattering](Subsurface-Scattering) | `Subsurface` profiles, the Burley diffusion pass, and transmission |
| [Light Styles](Light-Styles) | `LightStyles` GLSL snippets that customise how a single light is evaluated |
| [Surface UI](Surface-UI) | `Surfaces`, widgets, layout, the reactive core, SDF text, and surface materials |
| [Quality](Quality) | `Quality` one-call presets for the screen-space effect stack |
| [Models](Models) | `Models`/`Model`/`ModelInstance`, PBR materials, and the `Animator` |
| [Scene Capture](Scene-Capture) | `PerspectiveCapture`, `PerspectiveView`, and `PlanarReflection` mirrors |
| [Entity Effects](Entity-Effects) | `EntityEffects.surface`, `SurfaceConfig`, and `EntityTextureOverride` |
| [Mesh Tap](Mesh-Tap) | `EntityMeshTap` and `PosedMesh` for reading a posed entity's vertices |
| [Editor](Editor) | The in-game `AmneticEditor`, gizmos, and registering custom `Inspector` panels |
| [Shader Hot Reload](Shader-Hot-Reload) | Dev-only shader hot reloading and the `onReload` contract |

---

---

## Credits

- **[bb4j](https://github.com/Danrus1100/bb4j)** by [Danrus1100](https://github.com/Danrus1100) - the Blockbench `.bbmodel` parser behind Amnetic's Blockbench model loading.
- **[Veil](https://github.com/FoundryMC/Veil)** by [FoundryMC](https://github.com/FoundryMC) - a big source of inspiration for the shape of this library, particularly the deferred rendering and framebuffer work.
