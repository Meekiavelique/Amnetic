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
A Fabric rendering utility library for Minecraft 1.21.11 
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


## Docs

Full reference are in the [wiki](../../wiki):

- [Home](../../wiki/Home) - overview and navigation
- [Getting Started](../../wiki/Getting-Started) - setup and first effect
- [Post-Processing](../../wiki/Post-Processing) - full API reference
- [Uniforms](../../wiki/Uniforms) - suppliers and block name conventions
- [Writing Shaders](../../wiki/Writing-Shaders) - pipeline format and GLSL conventions

---

## IA USAGE
The only things i used ia for in this project is to correct my spelling and coherence in the wiki

## License

See [LICENSE.txt](LICENSE.txt).
