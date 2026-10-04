plugins {
    id("dev.kikugie.stonecutter")
    id("net.minecraftforge.gradle") version "[7.0.17,8)" apply false
    id("net.minecraftforge.jarjar") version "0.2.3" apply false
    id("net.minecraftforge.renamer") version "1.0.14" apply false
}

stonecutter active "26.1.2-fabric"

stonecutter parameters {
    constants.match(current.project.substringAfterLast('-'), "fabric", "forge")
    replacements {
        regex(current.parsed >= "1.21") {
            replace(
                "new (?:Identifier|ResourceLocation)\\(", "ResourceLocation.fromNamespaceAndPath(",
                "(?:Identifier|ResourceLocation)\\.fromNamespaceAndPath\\(", "new ResourceLocation("
            )
        }
        regex(current.parsed >= "1.21") {
            replace(
                "(?:Identifier|ResourceLocation)\\.tryParse\\(", "ResourceLocation.parse(",
                "(?:Identifier|ResourceLocation)\\.parse\\(", "ResourceLocation.tryParse("
            )
        }
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
        string(current.parsed >= "1.21.2") {
            replace(".getTimer()", ".getDeltaTracker()")
            replace(".getMainCamera().getPosition()", ".getMainCamera().position()")
        }
        string(current.parsed >= "1.21.5") {
            replace("GlStateManager.glShaderSource(shader, java.util.List.of(src))", "GlStateManager.glShaderSource(shader, src)")
        }
        string(current.parsed >= "1.20.5") {
            replace("level.chunk.ChunkStatus", "level.chunk.status.ChunkStatus")
        }
        // the GL state wrapper moved into the opengl backend package with the 1.21.5 device rewrite
        string(current.parsed >= "1.21.5") {
            replace("blaze3d.platform.GlStateManager", "blaze3d.opengl.GlStateManager")
        }
    }
}

val launchTargets = mapOf(
    "1201" to "1.20.1",
    "1211" to "1.21.1",
    "12111" to "1.21.11",
    "2612" to "26.1.2",
)

for ((suffix, minecraft) in launchTargets) {
    for (loader in listOf("Fabric", "Forge")) {
        tasks.register("run${loader}${suffix}Client") {
            group = "amnetic runs"
            description = "Launches the $loader client for Minecraft $minecraft."
            dependsOn(":$minecraft-${loader.lowercase()}:runClient")
        }
    }
}
