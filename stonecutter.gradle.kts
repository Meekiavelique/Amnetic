plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2"

stonecutter parameters {
    replacements {
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
        // the GL state wrapper moved into the opengl backend package with the 1.21.5 device rewrite
        string(current.parsed >= "1.21.5") {
            replace("blaze3d.platform.GlStateManager", "blaze3d.opengl.GlStateManager")
        }
    }
}
