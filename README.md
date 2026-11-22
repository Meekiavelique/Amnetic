<p align="center">
  <img src="https://files.catbox.moe/jdrud4.png" alt="Amnetic" />
</p>
<p align="center">

  <img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />

  <img src="https://img.shields.io/badge/Loaders-Fabric%20%7C%20Forge%20%7C%20NeoForge%20%7C%20Quilt-2C2C2C?style=for-the-badge&logo=openjdk&logoColor=white" alt="Fabric, Forge, NeoForge and Quilt" />
  <a href="https://github.com/Meekiavelique/Amnetic/wiki"><img src="https://img.shields.io/badge/Wiki-Documentation-4A90E2?style=for-the-badge&logo=gitbook&logoColor=white" alt="Wiki" /></a>
  <a href="https://discord.gg/avSH2JTfef"><img src="https://img.shields.io/badge/Discord-online-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord" /></a>
</p>


A rendering library for Minecraft: dynamic lights, materials, particles, post-processing and models.

## Versions

| Minecraft | Java | Fabric | Forge | NeoForge | Quilt |
| --- | --- | --- | --- | --- | --- |
| 1.20.1 | 17 | ✓ | ✓ | | Fabric jar |
| 1.21.1 | 21 | ✓ | ✓ | | Fabric jar |
| 1.21.11 | 21 | ✓ | ✓ | | Fabric jar |
| 26.1.2 | 25 | ✓ | ✓ | ✓ | Fabric jar |
| 26.2 | 25 | ✓ | ✓ | ✓ | Fabric jar |
| 26.3 | 25 | ✓ | ✓ | ✓ | Quilt jar |

- Fabric and Quilt need Fabric API. Fabric 26.3 needs Fabric Loader 0.20.1+.
- NeoForge 26.1.2 needs Forgified Fabric API. NeoForge 26.2 and 26.3 need nothing extra.

## Renderers

| Renderer | Status |
| --- | --- |
| OpenGL | Supported |
| Vulkan | Native on 26.2 and 26.3, older versions fall back to OpenGL |
| Sodium | 0.9.2 tested on 26.3 |
| Iris | Tested on 26.1.2 and 26.3 with OpenGL. Iris on Vulkan crashes |
| OptiFine | Untested |

## Using it in your mod

Grab the jar matching your Minecraft version and loader (not the sources or dev jar), drop it in `libs/`, then:

**Fabric / Quilt, 1.20.1 to 1.21.11**

```groovy
dependencies {
    modImplementation files("libs/amnetic.jar")
}
```

**Fabric / Quilt / Forge 26.x, NeoForge**

```groovy
dependencies {
    implementation files("libs/amnetic.jar")
}
```

**Forge, 1.20.1 to 1.21.11** (name the jar `amnetic-VERSION.jar`)

```groovy
repositories {
    flatDir { dirs "libs" }
}

dependencies {
    implementation fg.deobf("local:amnetic:VERSION")
}
```

Then add `amnetic` as a required dependency in your mod metadata. Players install Amnetic in their `mods` folder next to your mod.

Fabric builds are also published to `https://maven.meekhasto.rest` as `com.meekdev:amnetic:VERSION`.

Bundling Amnetic inside your mod (Jar-in-Jar) is not supported. Ship it as a separate dependency.

## Documentation

API and examples are on the [wiki](https://github.com/Meekiavelique/Amnetic/wiki). For shader integration see [Native shaders](wiki/Native-Shaders.md).

## Credits

- **[bb4j](https://github.com/Danrus1100/bb4j)** by [Danrus1100](https://github.com/Danrus1100), the Blockbench model parser, bundled in `libs/bb4j.jar`.
- **[Veil](https://github.com/FoundryMC/Veil)** by [FoundryMC](https://github.com/FoundryMC), a big inspiration for the deferred rendering and framebuffer side.

## License

See [LICENSE.txt](LICENSE.txt).
