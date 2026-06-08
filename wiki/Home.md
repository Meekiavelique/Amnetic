# Amnetic

Amnetic is a Minecraft 26.1.2 Fabric rendering utility library. 

---

## Current scope

**Post-processing effects**

Register fullscreen post-processing effects using JSON pipelines and shaders. Effects can be enabled conditionally, smoothly faded in or out, configured with dynamic uniform values from the game state, and applied at two points in the frame: before the HUD or after all rendering, including the GUI.

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

## Planned scope

The following areas are planned for future releases. None of them are available yet.

- Second-camera rendering
- FrameGraph pass injection
- Deferred lighting support

---

## Navigation

| Page | Description |
|---|---|
| [Getting Started](Getting-Started) | How to add Amnetic to your mod and register your first effect |
| [Post-Processing](Post-Processing) | Full reference for `PostEffects`, `PostEffectHandle`, `RenderPhase`, priority, and the fade system |
| [Uniforms](Uniforms) | `UniformSuppliers` reference and an explanation of how uniform blocks work |
| [Writing Shaders](Writing-Shaders) | JSON pipeline format, GLSL conventions, and a complete worked example |
| [Compute Shaders](Compute-Shaders) | `ComputeShader`, `ShaderStorageBuffer`, `ComputeTexture`, and the capability probe |
| [Particles](Particles) | The `Particles` facade, the billboard shader contract, and `SceneDepth` soft particles |
| [Instanced Rendering](Instanced-Rendering) | `InstancedMesh`, built-in shaders, layouts, `MeshData`, render state, and phases |
| [Camera](Camera) | `AmneticCamera` queries, `CameraEffects` + `CameraModifier`, and the `CameraDirector` |
| [Tweens](Tweens) | The `Animations` engine, `Tween` and `Timeline`, easing curves, and interpolators |
| [Mesh Pipeline](Mesh-Pipeline) | Generic world-space mesh render type for custom geometry |
| [Vanilla Rendering Internals](Vanilla-Rendering-Index) | How Minecraft 26.1.2 creates the GL context, loads pipelines/shaders, and handles depth |
| [Vanilla Window and OpenGL](Vanilla-Window-and-OpenGL) | GLFW hints, requested OpenGL version/profile, and what “forcing” a newer version entails |
| [Vanilla RenderSystem and GlBackend](Vanilla-RenderSystem-and-GlBackend) | Backend init, debug output, capabilities, and default uniform blocks |
| [Vanilla Shaders and Post Effects](Vanilla-Shaders-and-Post-Effects) | Post-effect JSON schema, sampler/uniform conventions, and pass execution |
| [Vanilla Depth and Fog](Vanilla-Depth-and-Fog) | Depth sampling, linearization math, and the “hands-only depth” pitfall |
| [Vanilla Advanced Shader Stages](Vanilla-Advanced-Shader-Stages) | What vanilla supports, how to force GL 4.x, and what it takes to use compute/tessellation |

---
