plugins {
    id("dev.kikugie.loom-back-compat")
    `maven-publish`
}

stonecutter {
    properties.tags(current.project.substringBeforeLast('-'))
}

version = "${findProperty("mod_version") ?: property("mod.version")}+${sc.current.version}"
group = property("mod.group") as String
base.archivesName = property("mod.id") as String

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}
val toolchainJava: JavaVersion = maxOf(requiredJava, JavaVersion.VERSION_21)
val loaderPlatform = providers.gradleProperty("loader_platform").getOrElse("fabric")
require(loaderPlatform in listOf("fabric", "quilt"))
val hasImGuiMc = sc.current.parsed >= "1.21"

repositories {
    maven("https://maven.quiltmc.org/repository/release")
    exclusiveContent {
        forRepository { maven("https://maven.ryanhcode.dev/releases") { name = "RyanHCode Maven" } }
        filter { includeGroup("foundry.imguimc") }
    }
}

dependencies {
    val mc = sc.current.version
    val lwjgl: String = sc.properties["deps.lwjgl"]
    val fabricApi: String = sc.properties["deps.fabric_api"]
    val imguimc = "foundry.imguimc:imguimc-fabric-${if (sc.current.parsed >= "26.1") "26.1" else mc}:${property("deps.imguimc")}"

    minecraft("com.mojang:minecraft:$mc")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    if (loaderPlatform == "quilt") {
        modLocalRuntime("org.quiltmc:quilt-loader:0.31.0-beta.4")
        modLocalRuntime("org.quiltmc:quilt-loader-dependencies:0.31.0-beta.4")
    }
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

    compileOnly("io.github.spair:imgui-java-binding:1.92.0")
    if (hasImGuiMc) {
        modCompileOnly(imguimc)
        modLocalRuntime(imguimc)
    }
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
    toolchain.languageVersion = JavaLanguageVersion.of(toolchainJava.majorVersion)
}

val absentClasses: List<String> = buildList {
    if (!hasImGuiMc) addAll(listOf("client/ui/AmneticEditor", "client/ui/TexturePreview", "client/ui/inspector/GBufferInspector"))
    if (sc.current.parsed < "1.20.2") add("mixin/accessor/LevelRendererAccessor")
    if (sc.current.parsed < "1.21.2") addAll(listOf("mixin/EntityRenderStateMixin", "mixin/EntityRendererMixin", "mixin/WeatherEffectRendererMixin"))
    if (sc.current.parsed < "1.21.5") addAll(listOf("mixin/GlCommandEncoderMixin", "client/render/MeshPipeline",
            "client/entityfx/internal/EntityEffectPipeline", "client/framebuffer/internal/WrappedGlTexture",
            "mixin/accessor/RenderTypeInvoker"))
    if (sc.current.parsed >= "1.21.5") add("mixin/accessor/LightTextureAccessor")
}

tasks {
    withType<JavaCompile>().configureEach {
        exclude("com/meekdev/amnetic/ForgeClientEvents.java")
        for (path in absentClasses) exclude("com/meekdev/amnetic/$path.java")
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "100000"))
        options.release = requiredJava.majorVersion.toInt()
    }

    processResources {
        exclude("META-INF/mods.toml")
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
        from(rootProject.file("LICENSE.txt")) { rename { "${it}_amnetic" } }
    }

    jar {
        from(zipTree(rootProject.file("libs/bb4j.jar"))) { exclude("META-INF/**") }
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

if (loaderPlatform == "quilt") {
    val prepareQuiltLibraries = tasks.register<Sync>("prepareQuiltLibraries") {
        dependsOn("processIncludeJars")
        from(layout.buildDirectory.dir("processIncludeJars")) {
            include("jackson-*.jar", "jgltf-*.jar")
        }
        into(layout.buildDirectory.dir("run/quilt/development-libraries"))
    }
    tasks.withType<JavaExec>().matching { it.name.startsWith("run") }.configureEach {
        dependsOn(prepareQuiltLibraries)
        // Quilt supplies Fabric compatibility classes. Model libraries load as mod-owned jars,
        // keeping Jackson off the application loader and preventing split-classloader errors.
        classpath = classpath.filter { !it.name.startsWith("fabric-loader-") && !it.name.startsWith("jackson-") && !it.name.startsWith("jgltf-") }
        systemProperty("loader.development", "true")
        systemProperty("loader.classPathGroups", sourceSets.main.get().output.files.joinToString(System.getProperty("path.separator")) { it.absolutePath })
        systemProperty("loader.modsDir", layout.buildDirectory.dir("run/quilt/development-libraries").get().asFile.absolutePath)
        // Retain Loom's injector for its asset arguments and launch.cfg.
        doFirst {
            jvmArgs = (jvmArgs ?: emptyList()).filter { !it.startsWith("-Dfabric.dli.main=") }
            systemProperty("fabric.dli.main", if (name.contains("Server")) "org.quiltmc.loader.impl.launch.knot.KnotServer" else "org.quiltmc.loader.impl.launch.knot.KnotClient")
        }
    }
}
