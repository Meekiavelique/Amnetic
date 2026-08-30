<p align="center">
  <img src="https://files.catbox.moe/jdrud4.png" alt="Java" />
</p>
<p align="center">

  <img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />

  <img src="https://img.shields.io/badge/Running%20on-Fabric-2C2C2C?style=for-the-badge&logo=openjdk&logoColor=white" alt="Running on Fabric" />
  <a href=" "><img src="https://img.shields.io/badge/Wiki-Documentation-4A90E2?style=for-the-badge&logo=gitbook&logoColor=white" alt="Wiki" /></a>
  <a href="https://discord.gg/avSH2JTfef"><img src="https://img.shields.io/badge/Discord-online-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord" />
</p>

<p align="center">
A Fabric rendering utility library for Minecraft 26.1.2 
</p>

## Installation

Amnetic is a **standalone mod**. Install it as a separate mod (e.g. from Modrinth)
alongside any mod that uses it. Please **do not bundle it (Jar-in-Jar) inside your own mod**.
Bundling is unsupported: a single shared install is supportable, and when multiple mods
each ship their own copy, Fabric loads one and shadows the rest, causing version
mismatches and conflicts. Amnetic logs a warning if it detects it was loaded nested.

**Players:** download Amnetic from Modrinth and drop it in your `mods` folder next to the
mods that depend on it.

**Developers:** depend on it without bundling. Add the repository and a `modImplementation`
dependency to your `build.gradle`:

```groovy
repositories {
    maven { url "https://maven.meekhasto.rest" }
}

dependencies {
    // modImplementation (not `include`) Amnetic should ships as its own mod
    modImplementation "com.meekdev:amnetic:{version}"
}
```

Then declare it as a dependency in your `fabric.mod.json` so users are pointed to install it:

```json
"depends": {
    "amnetic": ">={version}"
}
```


## Features

- **Post-processing** - fullscreen effects from JSON pipelines, conditional, faded, with dynamic uniforms.
- **Compute shaders** - dispatch `.comp` programs with SSBOs and textures, behind a capability probe.
- **Particles** - CPU-simulated, GPU-billboarded, with affectors, soft depth, and surface colliders.
- **Instanced rendering** - one mesh, thousands of instances, custom layouts and shaders.
- **World meshes** - generic world-space geometry render type with your own shaders.
- **Camera** - queries, additive effects (shake/kick/FOV), and a cinematic director.
- **Animation** - a tween/timeline engine with easing, repeat, and interpolators.
- **Framebuffers** - off-screen render targets, MRT, depth capture, main-frame blits, ping-pong passes.
- **Bloom** - emissive/bright-pixel glow over the deferred renderer.
- **Deferred lights** - point, spot, directional, area, and tube lights, lit from depth and normals.
- **Shadows** - per-light depth-mapped shadows for spot and point lights (soft PCF/PCSS, coloured, entity occluders).
- **Volumetric god-rays** - per-light single-scattering shafts at reduced resolution with temporal reprojection.
- **Screen-space AO/GI** - SSAO (with temporal denoise) and a one-bounce SSGI pass.
- **Decals** - projected box decals that can also write the gbuffer (normal/roughness) to relight surfaces.
- **Particle editor** - in-game tool to author particle effects (curves, gradient, affectors, trails) and save/load them as JSON.
- **Models (glTF / OBJ)** - loaded into the instanced pipeline with PBR metallic-roughness materials, deferred-lit (clip-based animation player included; geometry skinning not yet implemented).
- **Scene capture** - render the world from a virtual camera; planar-reflection mirrors.
- **Entity effects** - run a custom shader over an entity's body, or override its texture.
- **Mesh tap** - read a posed entity's vertices for particles and custom geometry.

Planned: FrameGraph pass injection.


## Docs

Full reference are in the [wiki](../../wiki):

- [Home](../../wiki/Home) - overview and navigation
- [Getting Started](../../wiki/Getting-Started) - setup and first effect
- [Post-Processing](../../wiki/Post-Processing) - full API reference
- [Uniforms](../../wiki/Uniforms) - suppliers and block name conventions
- [Writing Shaders](../../wiki/Writing-Shaders) - pipeline format and GLSL conventions
- [Compute Shaders](../../wiki/Compute-Shaders) - compute programs, SSBOs, and textures
- [Particles](../../wiki/Particles) - particle system and soft particles
- [Instanced Rendering](../../wiki/Instanced-Rendering) - GPU instancing helpers
- [Camera](../../wiki/Camera) - queries, effects, and the cinematic director
- [Tweens](../../wiki/Tweens) - the animation engine, tweens, timelines, and easing
- [Mesh Pipeline](../../wiki/Mesh-Pipeline) - custom world-space geometry
- [Framebuffers](../../wiki/Framebuffers) - off-screen render targets and ping-pong passes
- [Bloom](../../wiki/Bloom) - the emissive glow pass
- [Deferred Lights](../../wiki/Deferred-Lights) - dynamic point/spot/area lights
- [Shadows](../../wiki/Shadows) - per-light depth-mapped shadows
- [Screen-Space Effects](../../wiki/Screen-Space-Effects) - SSAO and SSGI
- [Particle Editor](../../wiki/Particle-Editor) - authoring effects in-game
- [Models](../../wiki/Models) - glTF/OBJ loading, PBR materials, and animation
- [Scene Capture](../../wiki/Scene-Capture) - virtual cameras and planar reflections
- [Entity Effects](../../wiki/Entity-Effects) - custom shaders over entities, texture overrides
- [Mesh Tap](../../wiki/Mesh-Tap) - reading a posed entity's vertices
- [Shading Models](../../wiki/Shading-Models) - lighting bases and custom GLSL shading snippets
- [Subsurface Scattering](../../wiki/Subsurface-Scattering) - Burley diffusion and transmission
- [Light Styles](../../wiki/Light-Styles) - GLSL snippets that customise a single light
- [Surface UI](../../wiki/Surface-UI) - widgets, layout, reactivity, and SDF text

---

## IA USAGE
The only things i used ia for in this project is to correct my spelling and coherence in the wiki

## Credits

- **[bb4j](https://github.com/Danrus1100/bb4j)** by [Danrus1100](https://github.com/Danrus1100) - the Blockbench `.bbmodel` parser Amnetic uses to load Blockbench models. Bundled in `libs/bb4j.jar`.
- **[Veil](https://github.com/FoundryMC/Veil)** by [FoundryMC](https://github.com/FoundryMC) - a big source of inspiration for the shape of this library, particularly the deferred rendering and framebuffer work.

## License

See [LICENSE.txt](LICENSE.txt).
