pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.minecraftforge.net/") { name = "MinecraftForge" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

stonecutter {
    create(rootProject) {
        fun target(minecraft: String, loader: String, script: String) {
            version("$minecraft-$loader", minecraft).buildscript = script
        }

        for (minecraft in listOf("1.20.1", "1.21.1", "1.21.11", "26.1.2")) {
            target(minecraft, "fabric", "build.gradle.kts")
            target(minecraft, "forge", "build.forge.gradle")
        }
        vcsVersion = "26.1.2-fabric"
    }
}

rootProject.name = "amnetic"

include("examples")
if (file("bench").isDirectory) {
    include("bench")
}
include("neoforge")
