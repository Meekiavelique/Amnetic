import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("dev.kikugie.loom-back-compat")
    `maven-publish`
    id("com.gradleup.shadow")
}

stonecutter {
    properties.tags(current.project.substringBeforeLast('-'))
}

version = "${findProperty("mod_version") ?: property("mod.version")}+${sc.current.version}"
group = property("mod.group") as String
base.archivesName = (property("mod.id") as String) + if (sc.current.project.endsWith("-quilt")) "-quilt" else ""

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}
val toolchainJava: JavaVersion = maxOf(requiredJava, JavaVersion.VERSION_21)
val fabricLoaderVersion = if (sc.current.parsed >= "26.3") "0.20.1" else property("deps.fabric_loader") as String
val loaderPlatform = providers.gradleProperty("loader_platform").getOrElse(if (sc.current.project.endsWith("-quilt")) "quilt" else "fabric")
require(loaderPlatform in listOf("fabric", "quilt"))
val hasImGuiMc = sc.current.parsed >= "1.21" && sc.current.parsed < "26.2"

repositories {
    maven("https://maven.quiltmc.org/repository/release")
    exclusiveContent {
        forRepository { maven("https://maven.ryanhcode.dev/releases") { name = "RyanHCode Maven" } }
        filter { includeGroup("foundry.imguimc") }
    }
}

val modelLibraries by configurations.creating
configurations.implementation { extendsFrom(modelLibraries) }

dependencies {
    val testLwjgl: String = sc.properties["deps.lwjgl"]
    for (module in listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl")) {
        testImplementation("org.lwjgl:$module:$testLwjgl")
        for (platform in listOf("natives-windows", "natives-linux", "natives-macos", "natives-macos-arm64")) testRuntimeOnly("org.lwjgl:$module:$testLwjgl:$platform")
    }
    val mc = sc.current.version
    val lwjgl: String = sc.properties["deps.lwjgl"]
    val fabricApi: String = sc.properties["deps.fabric_api"]
    val imguimcMc = when {
        sc.current.parsed >= "26.2" -> "26.2"
        sc.current.parsed >= "26.1" -> "26.1"
        else -> mc
    }
    val imguimc = "foundry.imguimc:imguimc-fabric-$imguimcMc:${property("deps.imguimc")}"

    minecraft("com.mojang:minecraft:$mc")
    loomx.applyMojangMappings()
    if (loaderPlatform == "quilt") {
        modImplementation("org.quiltmc:quilt-loader:0.31.0-beta.4")
        modImplementation("org.quiltmc:quilt-loader-dependencies:0.31.0-beta.4")
    } else modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApi")

    modelLibraries("de.javagl:jgltf-model:2.0.4")
    modelLibraries("de.javagl:jgltf-impl-v2:2.0.4")
    modelLibraries("de.javagl:jgltf-impl-v1:2.0.4")
    modelLibraries("com.fasterxml.jackson.core:jackson-databind:2.13.4.2")
    modelLibraries("com.fasterxml.jackson.core:jackson-core:2.13.4")
    modelLibraries("com.fasterxml.jackson.core:jackson-annotations:2.13.4")

    implementation(files(rootProject.file("libs/bb4j.jar")))

    implementation("org.lwjgl:lwjgl-assimp:$lwjgl")
    include("org.lwjgl:lwjgl-assimp:$lwjgl")
    for (platform in listOf("natives-windows", "natives-linux", "natives-macos", "natives-macos-arm64")) {
        runtimeOnly("org.lwjgl:lwjgl-assimp:$lwjgl:$platform")
        include("org.lwjgl:lwjgl-assimp:$lwjgl:$platform")
    }

    if (sc.current.parsed >= "26.2") {
        include(implementation("io.github.spair:imgui-java-binding:1.92.0")!!)
        for (platform in listOf("windows", "linux", "macos")) include(runtimeOnly("io.github.spair:imgui-java-natives-$platform:1.92.0")!!)
        include(implementation("org.lwjgl:lwjgl-tinyfd:$lwjgl")!!)
        for (platform in listOf("natives-windows", "natives-linux", "natives-macos", "natives-macos-arm64")) include(runtimeOnly("org.lwjgl:lwjgl-tinyfd:$lwjgl:$platform")!!)
    } else compileOnly("io.github.spair:imgui-java-binding:1.92.0")
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
    if (sc.current.parsed < "26.2") addAll(listOf("mixin/FirstPersonItemMixin", "mixin/RenderPearlGameRendererMixin", "mixin/accessor/GameRendererAccessor", "client/framebuffer/internal/NativeFramebuffer", "client/grade/internal/NativeColorGrade", "client/ui/NativeEditorBackend", "client/ssao/internal/NativeSsaoPass", "client/shadow/internal/NativeLocalShadows", "client/light/internal/NativeDeferredLighting", "client/gbuffer/internal/NativeNormalFill", "client/render/NativeScreenProgram", "client/render/NativePassDescriptors", "client/render/NativeShaderSource262", "client/instanced/internal/NativeInstanceRenderer", "client/post/internal/NativePostPipeline", "client/decal/internal/NativeDecalRenderer", "client/light/internal/TiledLighting", "client/light/internal/ScatteringLights"))
    if (sc.current.parsed < "26.3") addAll(listOf("mixin/NativeTextureUnitsMixin", "mixin/NativeCommandBatchMixin", "mixin/NativeLevelStagesMixin", "mixin/NativeMaterialBindMixin", "mixin/NativeMaterialDisposeMixin", "mixin/NativeMaterialPassMixin", "mixin/NativeMaterialPipelinesMixin", "client/gbuffer/internal/NativeWorldMaterials", "client/pipeline/internal/NativeLayers"))
    if (sc.current.parsed >= "26.2") addAll(listOf("mixin/IrisPipelineMixin", "mixin/GameRendererMixin", "mixin/GlCommandEncoderMixin", "mixin/ItemInHandRendererMixin", "mixin/WindowGlContextMixin"))
    if (sc.current.version == "26.2") add("mixin/FirstPersonItemMixin")
    if (sc.current.parsed >= "26.3") addAll(listOf("client/render/NativePassDescriptors", "client/render/NativeShaderSource262"))
    if (sc.current.parsed < "1.21") addAll(listOf("client/ui/AmneticEditor", "client/ui/TexturePreview", "client/ui/inspector/GBufferInspector"))
    if (sc.current.parsed < "1.20.2") add("mixin/accessor/LevelRendererAccessor")
    if (sc.current.parsed < "1.21.2") addAll(listOf("mixin/EntityRenderStateMixin", "mixin/EntityRendererMixin", "mixin/WeatherEffectRendererMixin"))
    if (sc.current.parsed < "1.21.5") addAll(listOf("mixin/GlCommandEncoderMixin", "client/render/MeshPipeline",
            "client/entityfx/internal/EntityEffectPipeline", "client/framebuffer/internal/WrappedGlTexture",
            "mixin/accessor/RenderTypeInvoker"))
    if (sc.current.parsed >= "1.21.5") addAll(listOf("mixin/accessor/LightTextureAccessor", "mixin/BufferUploaderMixin"))
    if (sc.current.parsed < "26.2") add("mixin/accessor/GameRendererAccessor")
    if (sc.current.parsed < "1.21" || sc.current.parsed >= "1.21.5") add("client/gbuffer/internal/TerrainNormalFill")
    if (sc.current.parsed < "1.21" || sc.current.parsed >= "1.21.5") add("client/gbuffer/internal/BlockPbrAtlas")
}

tasks {
    withType<JavaCompile>().configureEach {
        exclude("com/meekdev/amnetic/ForgeClientEvents.java", "com/meekdev/amnetic/NeoForgeClientEvents.java")
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
            "loader_version" to if (loaderPlatform == "quilt") "0.19.5" else fabricLoaderVersion,
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
    loom.runs.configureEach {
        mainClass.set(if (name.contains("server", ignoreCase = true)) "org.quiltmc.loader.impl.launch.knot.KnotServer" else "org.quiltmc.loader.impl.launch.knot.KnotClient")
        runDir("build/run/quilt")
    }
    val prepareQuiltMods = tasks.register<Sync>("prepareQuiltMods") {
        dependsOn(tasks.named("jar"))
        from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
        from(configurations.named("runtimeClasspath").map { cp ->
            cp.filter { it.name.startsWith("fabric-api-") || it.name.startsWith("imguimc-fabric-") }
        })
        into(layout.buildDirectory.dir("run/quilt/mods"))
    }
    tasks.withType<JavaExec>().matching { it.name.startsWith("run") }.configureEach {
        dependsOn(prepareQuiltMods)
        systemProperty("loader.transform_cache.disable_external_references", "true")
        workingDir(layout.buildDirectory.dir("run/quilt"))
    }
}

val shadedModelJar = tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(modelLibraries)
    archiveClassifier = "dev-shadow"
    relocate("de.javagl", "com.meekdev.amnetic.internal.model.javagl")
    relocate("com.fasterxml.jackson", "com.meekdev.amnetic.internal.model.jackson")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    from(zipTree(rootProject.file("libs/bb4j.jar"))) { exclude("META-INF/**") }
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
}
tasks.withType<RemapJarTask>().configureEach {
    if (name == "remapJar") inputFile.set(shadedModelJar.flatMap { it.archiveFile })
}
val presetSecurityRegression = tasks.register<JavaExec>("presetSecurityRegression") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.meekdev.amnetic.client.particle.editor.EffectPresetFilesRegression"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(toolchainJava.majorVersion) }
}
// a small heap, so a decoder that allocates what a hostile file claims fails here instead of passing
val hostileContentRegression = tasks.register<JavaExec>("hostileContentRegression") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.meekdev.amnetic.client.content.HostileContentRegression"
    maxHeapSize = "256m"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(toolchainJava.majorVersion) }
}
tasks.named("check") { dependsOn(presetSecurityRegression, hostileContentRegression) }
tasks.withType<Test>().configureEach { failOnNoDiscoveredTests = false }

tasks.register<JavaExec>("lightingRegression") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.meekdev.amnetic.client.light.internal.LightingRegression"
    workingDir = rootProject.projectDir
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(toolchainJava.majorVersion) }
}

if (sc.current.parsed >= "26.1") {
    val unshadedJar = tasks.named<Jar>("jar") { archiveClassifier = "unshaded" }
    val productionJar = tasks.register<Jar>("productionJar") {
        dependsOn(unshadedJar, shadedModelJar)
        archiveClassifier = ""
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(unshadedJar.flatMap { it.archiveFile }.map { zipTree(it) }) {
            include("fabric.mod.json", "META-INF/jars/**")
        }
        from(shadedModelJar.flatMap { it.archiveFile }.map { zipTree(it) }) {
            exclude("fabric.mod.json", "META-INF/jars/**")
        }
    }
    tasks.named("assemble") { dependsOn(productionJar) }
    publishing.publications.named<MavenPublication>("mavenJava") {
        setArtifacts(listOf(productionJar, tasks.named("sourcesJar")))
    }
}

apply(from = rootProject.file("gradle/client-runtime.gradle"))
