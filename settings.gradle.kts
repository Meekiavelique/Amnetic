pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

stonecutter {
    create(rootProject) {
        versions("1.21.1", "1.21.11", "26.1.2")
        vcsVersion = "26.1.2"
    }
}

rootProject.name = "amnetic"

include("examples")
