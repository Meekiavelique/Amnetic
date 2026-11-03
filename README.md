<p align="center">
  <img src="https://files.catbox.moe/jdrud4.png" alt="Amnetic" />
</p>
<p align="center">

  <img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />

  <img src="https://img.shields.io/badge/Loaders-Fabric%20%7C%20Forge%20%7C%20NeoForge%20%7C%20Quilt-2C2C2C?style=for-the-badge&logo=openjdk&logoColor=white" alt="Fabric, Forge, NeoForge and Quilt" />
  <a href="https://github.com/Meekiavelique/Amnetic/wiki"><img src="https://img.shields.io/badge/Wiki-Documentation-4A90E2?style=for-the-badge&logo=gitbook&logoColor=white" alt="Wiki" /></a>
  <a href="https://discord.gg/avSH2JTfef"><img src="https://img.shields.io/badge/Discord-online-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord" /></a>
</p>


A rendering library for Minecraft with dynamic lights, materials, particles, post-processing and model rendering.

## Compatibility

| Minecraft | Fabric | Forge | NeoForge | Quilt |
| --- | --- | --- | --- | --- |
| 1.20.1 | Yes | Yes | No | Fabric build |
| 1.21.1 | Yes | Yes | No | Fabric build |
| 1.21.11 | Yes | Yes | No | Fabric build |
| 26.1.2 | Yes | Yes | Yes | Fabric build |
| 26.2 | Yes | Yes | Yes | Fabric build |
| 26.3 | Yes | Yes | Yes | Separate Quilt build |

Fabric and Quilt require Fabric API. NeoForge 26.1.2 requires Forgified Fabric API; NeoForge 26.2 and 26.3 do not require it or Connector. Fabric 26.3 requires Fabric Loader 0.20.1 or newer. Use the separate Quilt artifact on 26.3.

Use Java 17 for 1.20.1, Java 21 for 1.21.x, and Java 25 for 26.x. Older Quilt versions use Fabric compatibility; they have not all been tested individually.

| Renderer | Compatibility |
| --- | --- |
| OpenGL | Supported. |
| Vulkan | Native rendering on 26.2 and 26.3; in-world lighting tested on all four loaders on 26.3. Earlier versions use OpenGL. |
| Sodium | 0.9.2 tested on 26.3 with Fabric and NeoForge on OpenGL/Vulkan, and Quilt on Vulkan. |
| Iris + Sodium | Tested on Fabric 26.1.2 and Fabric/Quilt 26.3 with OpenGL. On 26.3, Iris 1.11.7 and Complementary Reimagined r5.9.3 were tested. Iris Vulkan crashes, and the NeoForge shader-pack failure also occurs without Amnetic. |
| OptiFine | Unverified across the supported versions, including Forge and NeoForge. No confirmed 26.3 combination. |

Other renderer/version combinations are unverified. Loader support does not mean every shader pack or renderer combination has been tested.

## Add Amnetic to your mod

Choose the production JAR for the same Minecraft version and loader as your mod. Do not use a sources, dev, slim or unshaded JAR. Players can install it in their `mods` folder alongside your mod.

For development, put that JAR in your project's `libs` directory. These examples use Groovy Gradle; replace `amnetic.jar` with its filename.

**Fabric / Quilt with remapping Loom (1.20.1–1.21.11):**

```groovy
dependencies {
    modImplementation files("libs/amnetic.jar")
}
```

**Fabric / Quilt with non-remapping Loom (26.x), or NeoForge with ModDevGradle:**

```groovy
dependencies {
    implementation files("libs/amnetic.jar")
}
```

**Forge with ForgeGradle (1.20.1–1.21.11):** put the JAR in `libs` as `amnetic-VERSION.jar` and use:

```groovy
repositories {
    flatDir { dirs "libs" }
}

dependencies {
    implementation fg.deobf("local:amnetic:VERSION")
}
```

For Forge 26.x, use `implementation files("libs/amnetic.jar")` without deobfuscation.

Declare `amnetic` as a required dependency in your loader's mod metadata, with the version range your mod supports. Fabric uses `depends`, Quilt uses `quilt_loader.depends`, and Forge/NeoForge use their TOML dependency entries.

Published Fabric builds can also be resolved from `https://maven.meekhasto.rest` as `com.meekdev:amnetic:VERSION`. Use an existing published version matching your Minecraft target; a build in this repository is not necessarily published there.

### Embedding

To package the dependency inside a Fabric or Quilt mod, add this alongside the dependency above:

```groovy
dependencies {
    include files("libs/amnetic.jar")
}
```

Forge and NeoForge use their loader's Jar-in-Jar packaging: [ForgeGradle](https://docs.minecraftforge.net/en/fg-5.x/dependencies/jarinjar/) or [ModDevGradle](https://github.com/neoforged/ModDevGradle#jar-in-jar). Keep Amnetic as a complete nested mod JAR, including its metadata, resources and bundled dependencies.

Embedding has not been verified across these targets. Amnetic currently warns when loaded as a nested Fabric mod and treats bundling as unsupported; a separate installation remains the supported option.

## Documentation

See the [wiki](https://github.com/Meekiavelique/Amnetic/wiki) for the API and examples, and [Native shaders](wiki/Native-Shaders.md) for shader integration.

## Credits

- **[bb4j](https://github.com/Danrus1100/bb4j)** by [Danrus1100](https://github.com/Danrus1100) - the Blockbench `.bbmodel` parser Amnetic uses to load Blockbench models. Bundled in `libs/bb4j.jar`.
- **[Veil](https://github.com/FoundryMC/Veil)** by [FoundryMC](https://github.com/FoundryMC) - a big source of inspiration for the shape of this library, particularly the deferred rendering and framebuffer work.

## License

See [LICENSE.txt](LICENSE.txt).
