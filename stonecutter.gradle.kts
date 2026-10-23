plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2"

stonecutter parameters {
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
        // blend toggles take a draw buffer index since 26.2
        string(current.parsed >= "26.2") {
            replace("GlStateManager._enableBlend()", "GlStateManager._enableBlend(0)")
            replace("GlStateManager._disableBlend()", "GlStateManager._disableBlend(0)")
        }
        // the main target moved from Minecraft onto GameRenderer in 26.2
        string(current.parsed >= "26.2") {
            replace(".getMainRenderTarget()", ".gameRenderer.mainRenderTarget()")
        }
    }
}
