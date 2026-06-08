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

Not on Maven yet. Build artifacts will come soon

For now, clone and publish locally:

```bash
./gradlew publishToMavenLocal
```

Then in your `build.gradle`:

```groovy
repositories {
    mavenLocal()
}

dependencies {
    modImplementation "com.meekdev:amnetic:1.0-SNAPSHOT"
}
```


## Planned Features

**Framebuffer Utilities** - Off-screen render-target abstraction. It will support render-to-texture, ping-pong multi-passe buffers, main scene color/depth capture, MRT/G-buffer targets.

**Deferred Lights** - Point and spot lights (color, range, falloff, spot cone, specular). Uses full G-buffer lighting for Amnetic geometry, depth-normal for the vanilla world and support occlusion.

**Model Renderer (glTF / OBJ)** - Loads glTF 2.0 and OBJ files and renders them through the existing instanced/mesh pipeline. Includes PBR metallic-roughness materials, GPU instancing, G-buffer output (so models are automatically deferred-lit), and skeletal animation with GPU skinning.


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
- [Mesh Pipeline](../../wiki/Mesh-Pipeline) - custom world-space geometry

---

## IA USAGE
The only things i used ia for in this project is to correct my spelling and coherence in the wiki

## License

See [LICENSE.txt](LICENSE.txt).
