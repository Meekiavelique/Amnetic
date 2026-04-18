# Amnetic

Amnetic is a Minecraft 1.21.11 Fabric rendering utility library. 

---

## Current scope

**Post-processing effects**

Register fullscreen post-processing effects using JSON pipelines and shaders. Effects can be enabled conditionally, smoothly faded in or out, configured with dynamic uniform values from the game state, and applied at two points in the frame: before the HUD or after all rendering, including the GUI.


## Planned scope

The following areas are planned for future releases. None of them are available yet.

- World-space rendering utilities
- Second-camera rendering
- GPU instancing helpers
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
| [Vanilla Rendering Internals](Vanilla-Rendering-Index) | How Minecraft 1.21.11 creates the GL context, loads pipelines/shaders, and handles depth |
| [Vanilla Window and OpenGL](Vanilla-Window-and-OpenGL) | GLFW hints, requested OpenGL version/profile, and what “forcing” a newer version entails |
| [Vanilla RenderSystem and GlBackend](Vanilla-RenderSystem-and-GlBackend) | Backend init, debug output, capabilities, and default uniform blocks |
| [Vanilla Shaders and Post Effects](Vanilla-Shaders-and-Post-Effects) | Post-effect JSON schema, sampler/uniform conventions, and pass execution |
| [Vanilla Depth and Fog](Vanilla-Depth-and-Fog) | Depth sampling, linearization math, and the “hands-only depth” pitfall |
| [Vanilla Advanced Shader Stages](Vanilla-Advanced-Shader-Stages) | What vanilla supports, how to force GL 4.x, and what it takes to use compute/tessellation |

---
