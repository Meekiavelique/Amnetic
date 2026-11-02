<p align="center">
  <img src="https://files.catbox.moe/jdrud4.png" alt="Java" />
</p>
<p align="center">

  <img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />

  <img src="https://img.shields.io/badge/Loaders-Fabric%20%7C%20Forge%20%7C%20NeoForge%20%7C%20Quilt-2C2C2C?style=for-the-badge&logo=openjdk&logoColor=white" alt="Fabric, Forge, NeoForge and Quilt" />
  <a href=" "><img src="https://img.shields.io/badge/Wiki-Documentation-4A90E2?style=for-the-badge&logo=gitbook&logoColor=white" alt="Wiki" /></a>
  <a href="https://discord.gg/avSH2JTfef"><img src="https://img.shields.io/badge/Discord-online-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord" />
</p>

<p align="center">
A rendering utility library for Minecraft. Fabric and Forge builds cover 1.20.1, 1.21.1, 1.21.11, 26.1.2, 26.2, and 26.3; NeoForge targets 26.1.2, 26.2, and 26.3. Quilt has a separate 26.3 artifact and uses the Fabric artifact on older versions.
</p>

## Development runs

Use a JDK matching the selected Minecraft version (Java 17 for 1.20.1, Java 21 for
1.21.x, and Java 25 for 26.x). Every Stonecutter target exposes the loader's
normal `runClient` task. Convenience aliases are available from the root:

```text
./gradlew runFabric1201Client   ./gradlew runForge1201Client
./gradlew runFabric1211Client   ./gradlew runForge1211Client
./gradlew runFabric12111Client  ./gradlew runForge12111Client
./gradlew runFabric2612Client   ./gradlew runForge2612Client
./gradlew runFabric262Client    ./gradlew runForge262Client
./gradlew runFabric263Client    ./gradlew runForge263Client
```

On Windows, replace `./gradlew` with `.\gradlew.bat`. IntelliJ also imports a
client launch configuration for every Forge target from ForgeGradle.

### NeoForge and Quilt

Build NeoForge for Minecraft 26.1.2 with `./gradlew :neoforge:build` and launch it
with `./gradlew :neoforge:runNeoForgeClient`. Install Forgified Fabric API alongside
Amnetic; ImGuiMC is optional for the editor.

For native NeoForge 26.2 and 26.3, use `:26.2-neoforge:build` or
`:26.3-neoforge:build`, and the corresponding `runClient` task. These targets
use native loader events and do not require Forgified Fabric API or Connector.

For Quilt 26.3, use `./gradlew :26.3-quilt:build` or
`./gradlew :26.3-quilt:runClient`. Install Fabric API alongside the Quilt artifact.
Older versions use the Fabric artifact and `-Ploader_platform=quilt` for development runs.

Build a version with `./gradlew :26.3-fabric:build --configure-on-demand`.
Artifacts are written to `versions/<minecraft>-<loader>/build/libs`; install the
JAR without a `sources`, `dev`, `slim`, or `unshaded` classifier.

### Lighting and materials

The shared lighting shaders preserve material colour under local illumination,
including in dark scenes. Spotlights clip scattering to their cone, respect
occluders, and soften shadow edges without sampling neighbouring atlas tiles.
26.2 and 26.3 use native GPU passes; 26.3 also captures vanilla and Sodium world
albedo during their original draws and lights the isolated first-person layer.
Older versions retain the OpenGL rendering path.

On the legacy OpenGL path, Balanced lighting uses 75% resolution for eight or
more local lights. High uses full resolution and Performance uses 50%; select
the mode in the editor or with `-Damnetic.lightingQuality=performance`.

With Asterion, Amnetic disables its own TAA and restores the vertex-array state
after legacy fullscreen passes. Fabric and Quilt bundle private copies of the
model-loading libraries to avoid dependency conflicts.

Sodium 0.9.2 works with the 26.3 lighting path on OpenGL and Vulkan. Iris 1.11.7
with Complementary Reimagined r5.9.3 works on Fabric and Quilt with OpenGL.
Iris currently fails on Vulkan; its NeoForge shader-pack path also fails without
Amnetic. OptiFine 26.3 compatibility is not verified.

See [Native shaders](wiki/Native-Shaders.md) for shader bindings and resource ownership.

## Installation

Amnetic is a **standalone mod**. Install it as a separate mod (e.g. from Modrinth)
alongside any mod that uses it. Please **do not bundle it (Jar-in-Jar) inside your own mod**.
Bundling is unsupported: a single shared install is supportable, and when multiple mods
each ship their own copy, Fabric loads one and shadows the rest, causing version
mismatches and conflicts. Amnetic logs a warning if it detects it was loaded nested.

**Players:** download the Amnetic build for your Minecraft version and loader, then drop it
in your `mods` folder next to the mods that depend on it.

**Fabric developers:** depend on it without bundling. Add the repository and a
`modImplementation` dependency to your `build.gradle`:

```groovy
repositories {
    maven { url "https://maven.meekhasto.rest" }
}

dependencies {
    // modImplementation (not `include`): Amnetic ships as its own mod
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
- [Emissive](../../wiki/Emissive) - glowing surfaces, block emissive, and the source hook
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
