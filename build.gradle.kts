plugins {
    id("dev.kikugie.loom-back-compat")
    `maven-publish`
}

version = "${findProperty("mod_version") ?: property("mod.version")}+${sc.current.version}"
group = property("mod.group") as String
base.archivesName = property("mod.id") as String

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

repositories {
    exclusiveContent {
        forRepository { maven("https://maven.ryanhcode.dev/releases") { name = "RyanHCode Maven" } }
        filter { includeGroup("foundry.imguimc") }
    }
    maven("https://repo.javagl.de/") { name = "javagl Maven" }
}

dependencies {
    val mc = sc.current.version
    val lwjgl: String = sc.properties["deps.lwjgl"]
    val fabricApi: String = sc.properties["deps.fabric_api"]
    val imguimc = "foundry.imguimc:imguimc-fabric-${if (sc.current.parsed >= "26.1") "26.1" else mc}:${property("deps.imguimc")}"

    minecraft("com.mojang:minecraft:$mc")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApi")

    include(implementation("de.javagl:jgltf-model:2.0.4")!!)
    include(implementation("de.javagl:jgltf-impl-v2:2.0.4")!!)
    include(implementation("com.fasterxml.jackson.core:jackson-databind:2.13.4.2")!!)
    include(implementation("com.fasterxml.jackson.core:jackson-core:2.13.4")!!)
    include(implementation("com.fasterxml.jackson.core:jackson-annotations:2.13.4")!!)

    implementation(files(rootProject.file("libs/bb4j.jar")))

    implementation("org.lwjgl:lwjgl-assimp:$lwjgl")
    include("org.lwjgl:lwjgl-assimp:$lwjgl")
    for (platform in listOf("natives-windows", "natives-linux", "natives-macos", "natives-macos-arm64")) {
        runtimeOnly("org.lwjgl:lwjgl-assimp:$lwjgl:$platform")
        include("org.lwjgl:lwjgl-assimp:$lwjgl:$platform")
    }

    modCompileOnly(imguimc)
    compileOnly("io.github.spair:imgui-java-binding:1.92.0")
    modLocalRuntime(imguimc)
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain.languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
}

val absentClasses: List<String> = buildList {
    if (sc.current.parsed < "1.21.2") addAll(listOf("mixin/EntityRenderStateMixin", "mixin/EntityRendererMixin", "mixin/WeatherEffectRendererMixin"))
    if (sc.current.parsed < "1.21.5") addAll(listOf("mixin/GlCommandEncoderMixin", "client/render/MeshPipeline",
            "client/entityfx/internal/EntityEffectPipeline", "client/framebuffer/internal/WrappedGlTexture"))
    if (sc.current.parsed >= "1.21.5") add("mixin/accessor/LightTextureAccessor")
}

tasks {
    withType<JavaCompile>().configureEach {
        for (path in absentClasses) exclude("com/meekdev/amnetic/$path.java")
        if (sc.current.parsed < "1.21.5") exclude("net/minecraft/client/renderer/rendertype/AmneticRenderTypeAccess.java")
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "100000"))
        options.release = requiredJava.majorVersion.toInt()
    }

    processResources {
        inputs.property("absentClasses", absentClasses)
        filesMatching("amnetic.mixins.json") {
            filter { line -> if (absentClasses.any { line.trim().trimEnd(',') == "\"${it.removePrefix("mixin/").replace('/', '.')}\"" }) "" else line }
        }

        val mcCompat: String = sc.properties["mod.mc_compat"]
        val props = mapOf(
            "version" to project.version.toString(),
            "minecraft_version" to mcCompat,
            "loader_version" to (project.property("deps.fabric_loader") as String),
            "java" to "JAVA_${requiredJava.majorVersion}",
        )
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching(listOf("fabric.mod.json", "amnetic.mixins.json")) { expand(props) }
    }

    withType<Jar> {
        from(rootProject.file("LICENSE")) { rename { "${it}_amnetic" } }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = property("mod.id") as String
            from(components["java"])
        }
    }
}
