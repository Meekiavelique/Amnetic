plugins {
    id("dev.kikugie.stonecutter")
    id("com.gradleup.shadow") version "9.6.1" apply false
    id("net.neoforged.moddev") version "2.0.147" apply false
    id("net.minecraftforge.gradle") version "[7.0.17,8)" apply false
    id("net.minecraftforge.jarjar") version "0.2.3" apply false
    id("net.minecraftforge.renamer") version "1.0.14" apply false
}

stonecutter active "26.3-fabric"

stonecutter parameters {
    constants.match(current.project.substringAfterLast('-').let { if (it == "quilt") "fabric" else it }, "fabric", "forge", "neoforge")
    replacements {
        if (current.parsed < "1.21.2") {
            string(true) { replace("level.getMinY()", "level.getMinBuildHeight()") }
            string(true) { replace("level.getMaxY()", "(level.getMaxBuildHeight() - 1)") }
            string(true) { replace("net.minecraft.client.model.player.PlayerModel", "net.minecraft.client.model.PlayerModel") }
        }
        if (current.parsed < "26.2") {
            string(true) { replace("renderpearl.api.pipeline.ColorTargetState", "blaze3d.pipeline.ColorTargetState") }
            string(true) { replace("renderpearl.api.pipeline.DepthStencilState", "blaze3d.pipeline.DepthStencilState") }
            string(true) { replace("renderpearl.api.pipeline.CompareOp", "blaze3d.platform.CompareOp") }
        }
        if (current.version == "1.20.1") {
            string(true) { replace("Identifier", "ResourceLocation") }
            string(true) { replace(".getDeltaTracker()", ".getTimer()") }
            string(true) { replace(".getMainCamera().position()", ".getMainCamera().getPosition()") }
            string(true) { replace("GlStateManager.glShaderSource(shader, src)", "GlStateManager.glShaderSource(shader, java.util.List.of(src))") }
            string(true) { replace("level.chunk.status.ChunkStatus", "level.chunk.ChunkStatus") }
            string(true) { replace("blaze3d.opengl.GlStateManager", "blaze3d.platform.GlStateManager") }
            regex(true) { replace("(?:Identifier|ResourceLocation)\\.fromNamespaceAndPath\\(", "new ResourceLocation(", "(?:Identifier|ResourceLocation)\\.fromNamespaceAndPath\\(", "new ResourceLocation(") }
            regex(true) { replace("(?:Identifier|ResourceLocation)\\.parse\\(", "ResourceLocation.tryParse(", "(?:Identifier|ResourceLocation)\\.parse\\(", "ResourceLocation.tryParse(") }
            string(true) { replace("renderpearl.backend.opengl.", "blaze3d.platform.") }
            string(true) { replace("renderpearl.api.buffers.", "blaze3d.buffers.") }
            string(true) { replace("renderpearl.api.textures.", "blaze3d.textures.") }
            string(true) { replace("renderpearl.api.vertex.", "blaze3d.vertex.") }
            string(true) { replace("renderpearl.api.pipeline.BlendFunction", "blaze3d.pipeline.BlendFunction") }
            string(true) { replace("renderpearl.api.pipeline.RenderPipeline", "blaze3d.pipeline.RenderPipeline") }
            string(true) { replace("renderpearl.api.pipeline.UniformType", "blaze3d.shaders.UniformType") }
            string(true) { replace(".gameRenderer.mainRenderTarget()", ".getMainRenderTarget()") }
            string(true) { replace(".mainCamera().position()", ".getMainCamera().getPosition()") }
            string(true) { replace(".mainCamera()", ".getMainCamera()") }
            string(true) { replace(".gameRenderState()", ".getGameRenderState()") }
            string(true) { replace(".getDeviceInfo().isZZeroToOne()", ".isZZeroToOne()") }
            string(true) { replace("GlStateManager._enableBlend(0)", "GlStateManager._enableBlend()") }
            string(true) { replace("GlStateManager._disableBlend(0)", "GlStateManager._disableBlend()") }
            string(true) { replace("InputConstants.Type.KEYBOARD", "InputConstants.Type.KEYSYM") }
            string(true) { replace(".gui.overlay()", ".getOverlay()") }
            string(true) { replace(".gui.setScreen(", ".setScreen(") }
            string(true) { replace(".gui.screen()", ".screen") }
            string(true) { replace(".hasDepth()", ".useDepth") }
        }
        if (current.version == "1.21.1") {
            string(true) { replace("Identifier", "ResourceLocation") }
            string(true) { replace(".getDeltaTracker()", ".getTimer()") }
            string(true) { replace(".getMainCamera().position()", ".getMainCamera().getPosition()") }
            string(true) { replace("GlStateManager.glShaderSource(shader, src)", "GlStateManager.glShaderSource(shader, java.util.List.of(src))") }
            string(true) { replace("level.chunk.ChunkStatus", "level.chunk.status.ChunkStatus") }
            string(true) { replace("blaze3d.opengl.GlStateManager", "blaze3d.platform.GlStateManager") }
            regex(true) { replace("new (?:Identifier|ResourceLocation)\\(", "ResourceLocation.fromNamespaceAndPath(", "new (?:Identifier|ResourceLocation)\\(", "ResourceLocation.fromNamespaceAndPath(") }
            regex(true) { replace("(?:Identifier|ResourceLocation)\\.tryParse\\(", "ResourceLocation.parse(", "(?:Identifier|ResourceLocation)\\.tryParse\\(", "ResourceLocation.parse(") }
            string(true) { replace("renderpearl.backend.opengl.", "blaze3d.platform.") }
            string(true) { replace("renderpearl.api.buffers.", "blaze3d.buffers.") }
            string(true) { replace("renderpearl.api.textures.", "blaze3d.textures.") }
            string(true) { replace("renderpearl.api.vertex.", "blaze3d.vertex.") }
            string(true) { replace("renderpearl.api.pipeline.BlendFunction", "blaze3d.pipeline.BlendFunction") }
            string(true) { replace("renderpearl.api.pipeline.RenderPipeline", "blaze3d.pipeline.RenderPipeline") }
            string(true) { replace("renderpearl.api.pipeline.UniformType", "blaze3d.shaders.UniformType") }
            string(true) { replace(".gameRenderer.mainRenderTarget()", ".getMainRenderTarget()") }
            string(true) { replace(".mainCamera().position()", ".getMainCamera().getPosition()") }
            string(true) { replace(".mainCamera()", ".getMainCamera()") }
            string(true) { replace(".gameRenderState()", ".getGameRenderState()") }
            string(true) { replace(".getDeviceInfo().isZZeroToOne()", ".isZZeroToOne()") }
            string(true) { replace("GlStateManager._enableBlend(0)", "GlStateManager._enableBlend()") }
            string(true) { replace("GlStateManager._disableBlend(0)", "GlStateManager._disableBlend()") }
            string(true) { replace("InputConstants.Type.KEYBOARD", "InputConstants.Type.KEYSYM") }
            string(true) { replace(".gui.overlay()", ".getOverlay()") }
            string(true) { replace(".gui.setScreen(", ".setScreen(") }
            string(true) { replace(".gui.screen()", ".screen") }
            string(true) { replace(".hasDepth()", ".useDepth") }
        }
        if (current.version == "1.21.11") {
            string(true) { replace("ResourceLocation", "Identifier") }
            string(true) { replace(".getTimer()", ".getDeltaTracker()") }
            string(true) { replace(".getMainCamera().getPosition()", ".getMainCamera().position()") }
            string(true) { replace("GlStateManager.glShaderSource(shader, java.util.List.of(src))", "GlStateManager.glShaderSource(shader, src)") }
            string(true) { replace("level.chunk.ChunkStatus", "level.chunk.status.ChunkStatus") }
            string(true) { replace("blaze3d.platform.GlStateManager", "blaze3d.opengl.GlStateManager") }
            regex(true) { replace("new (?:Identifier|ResourceLocation)\\(", "ResourceLocation.fromNamespaceAndPath(", "new (?:Identifier|ResourceLocation)\\(", "ResourceLocation.fromNamespaceAndPath(") }
            regex(true) { replace("(?:Identifier|ResourceLocation)\\.tryParse\\(", "ResourceLocation.parse(", "(?:Identifier|ResourceLocation)\\.tryParse\\(", "ResourceLocation.parse(") }
            string(true) { replace("renderpearl.backend.opengl.", "blaze3d.opengl.") }
            string(true) { replace("renderpearl.api.buffers.", "blaze3d.buffers.") }
            string(true) { replace("renderpearl.api.textures.", "blaze3d.textures.") }
            string(true) { replace("renderpearl.api.vertex.", "blaze3d.vertex.") }
            string(true) { replace("renderpearl.api.pipeline.BlendFunction", "blaze3d.pipeline.BlendFunction") }
            string(true) { replace("renderpearl.api.pipeline.RenderPipeline", "blaze3d.pipeline.RenderPipeline") }
            string(true) { replace("renderpearl.api.pipeline.UniformType", "blaze3d.shaders.UniformType") }
            string(true) { replace(".gameRenderer.mainRenderTarget()", ".getMainRenderTarget()") }
            string(true) { replace(".mainCamera().position()", ".getMainCamera().position()") }
            string(true) { replace(".mainCamera()", ".getMainCamera()") }
            string(true) { replace(".gameRenderState()", ".getGameRenderState()") }
            string(true) { replace(".getDeviceInfo().isZZeroToOne()", ".isZZeroToOne()") }
            string(true) { replace("GlStateManager._enableBlend(0)", "GlStateManager._enableBlend()") }
            string(true) { replace("GlStateManager._disableBlend(0)", "GlStateManager._disableBlend()") }
            string(true) { replace("InputConstants.Type.KEYBOARD", "InputConstants.Type.KEYSYM") }
            string(true) { replace(".gui.overlay()", ".getOverlay()") }
            string(true) { replace(".gui.setScreen(", ".setScreen(") }
            string(true) { replace(".gui.screen()", ".screen") }
            string(true) { replace(".hasDepth()", ".useDepth") }
        }
        if (current.version == "26.1.2") {
            string(true) { replace("ResourceLocation", "Identifier") }
            string(true) { replace(".getTimer()", ".getDeltaTracker()") }
            string(true) { replace(".getMainCamera().getPosition()", ".getMainCamera().position()") }
            string(true) { replace("GlStateManager.glShaderSource(shader, java.util.List.of(src))", "GlStateManager.glShaderSource(shader, src)") }
            string(true) { replace("level.chunk.ChunkStatus", "level.chunk.status.ChunkStatus") }
            string(true) { replace("blaze3d.platform.GlStateManager", "blaze3d.opengl.GlStateManager") }
            regex(true) { replace("new (?:Identifier|ResourceLocation)\\(", "ResourceLocation.fromNamespaceAndPath(", "new (?:Identifier|ResourceLocation)\\(", "ResourceLocation.fromNamespaceAndPath(") }
            regex(true) { replace("(?:Identifier|ResourceLocation)\\.tryParse\\(", "ResourceLocation.parse(", "(?:Identifier|ResourceLocation)\\.tryParse\\(", "ResourceLocation.parse(") }
            string(true) { replace("renderpearl.backend.opengl.", "blaze3d.opengl.") }
            string(true) { replace("renderpearl.api.buffers.", "blaze3d.buffers.") }
            string(true) { replace("renderpearl.api.textures.", "blaze3d.textures.") }
            string(true) { replace("renderpearl.api.vertex.", "blaze3d.vertex.") }
            string(true) { replace("renderpearl.api.pipeline.BlendFunction", "blaze3d.pipeline.BlendFunction") }
            string(true) { replace("renderpearl.api.pipeline.RenderPipeline", "blaze3d.pipeline.RenderPipeline") }
            string(true) { replace("renderpearl.api.pipeline.UniformType", "blaze3d.shaders.UniformType") }
            string(true) { replace(".gameRenderer.mainRenderTarget()", ".getMainRenderTarget()") }
            string(true) { replace(".mainCamera().position()", ".getMainCamera().position()") }
            string(true) { replace(".mainCamera()", ".getMainCamera()") }
            string(true) { replace(".gameRenderState()", ".getGameRenderState()") }
            string(true) { replace(".getDeviceInfo().isZZeroToOne()", ".isZZeroToOne()") }
            string(true) { replace("GlStateManager._enableBlend(0)", "GlStateManager._enableBlend()") }
            string(true) { replace("GlStateManager._disableBlend(0)", "GlStateManager._disableBlend()") }
            string(true) { replace("InputConstants.Type.KEYBOARD", "InputConstants.Type.KEYSYM") }
            string(true) { replace(".gui.overlay()", ".getOverlay()") }
            string(true) { replace(".gui.setScreen(", ".setScreen(") }
            string(true) { replace(".gui.screen()", ".screen") }
            string(true) { replace(".hasDepth()", ".useDepth") }
        }
        if (current.version == "26.2") {
            string(true) { replace("InputConstants.Type.KEYBOARD", "InputConstants.Type.KEYSYM") }
            string(true) { replace("ResourceLocation", "Identifier") }
            string(true) { replace(".getTimer()", ".getDeltaTracker()") }
            string(true) { replace(".getMainCamera().getPosition()", ".mainCamera().position()") }
            string(true) { replace(".mainCamera().getPosition()", ".mainCamera().position()") }
            string(true) { replace("GlStateManager.glShaderSource(shader, java.util.List.of(src))", "GlStateManager.glShaderSource(shader, src)") }
            string(true) { replace("level.chunk.ChunkStatus", "level.chunk.status.ChunkStatus") }
            string(true) { replace("blaze3d.platform.GlStateManager", "blaze3d.opengl.GlStateManager") }
            string(true) { replace("com.mojang.renderpearl.api.commands.RenderPassDescriptor.builder(", "com.meekdev.amnetic.client.render.NativePassDescriptors.builder(") }
            string(true) { replace("RenderPassDescriptor.builder(", "com.meekdev.amnetic.client.render.NativePassDescriptors.builder(") }
            string(true) { replace("renderpearl.backend.opengl.", "blaze3d.opengl.") }
            string(true) { replace("renderpearl.api.pipeline.PrimitiveTopology", "blaze3d.PrimitiveTopology") }
            string(true) { replace("renderpearl.api.pipeline.ShaderSource", "com.meekdev.amnetic.client.render.NativeShaderSource262") }
            string(true) { replace("renderpearl.api.pipeline.ShaderType", "blaze3d.shaders.ShaderType") }
            string(true) { replace("CompiledRenderPipeline", "RenderPipeline") }
            string(true) { replace("renderpearl.api.pipeline.*;", "blaze3d.pipeline.*;\nimport com.mojang.blaze3d.shaders.*;\nimport com.mojang.blaze3d.PrimitiveTopology;\nimport com.mojang.blaze3d.IndexType;\nimport com.mojang.blaze3d.platform.*;\nimport com.meekdev.amnetic.client.render.NativeShaderSource262;") }
            string(true) { replace(".getMainRenderTarget()", ".gameRenderer.mainRenderTarget()") }
            string(true) { replace(".getMainCamera()", ".mainCamera()") }
            string(true) { replace(".getGameRenderState()", ".gameRenderState()") }
            string(true) { replace(".getOverlay()", ".gui.overlay()") }
            string(true) { replace(".setScreen(", ".gui.setScreen(") }
            string(true) { replace("mc.screen", "mc.gui.screen()") }
            string(true) { replace("minecraft.screen", "minecraft.gui.screen()") }
            string(true) { replace("Minecraft.getInstance().screen", "Minecraft.getInstance().gui.screen()") }
            string(true) { replace("GlStateManager._enableBlend()", "GlStateManager._enableBlend(0)") }
            string(true) { replace("GlStateManager._disableBlend()", "GlStateManager._disableBlend(0)") }
            string(true) { replace(".hasDepth()", ".useDepth") }
            string(true) { replace(".isZZeroToOne()", ".getDeviceInfo().isZZeroToOne()") }
            string(true) { replace("renderpearl.api.pipeline.IndexType", "blaze3d.IndexType") }
            string(true) { replace("renderpearl.api.device.", "blaze3d.systems.") }
            string(true) { replace("renderpearl.api.commands.", "blaze3d.systems.") }
            string(true) { replace("renderpearl/api/buffers/", "blaze3d/buffers/") }
            string(true) { replace("renderpearl/api/commands/", "blaze3d/systems/") }
            string(true) { replace("renderpearl/api/textures/", "blaze3d/textures/") }
            string(true) { replace("renderpearl.api.GpuFormat", "blaze3d.GpuFormat") }
            string(true) { replace("renderpearl.api.buffers.", "blaze3d.buffers.") }
            string(true) { replace("renderpearl.api.textures.", "blaze3d.textures.") }
            string(true) { replace("renderpearl.api.vertex.", "blaze3d.vertex.") }
            string(true) { replace("renderpearl.api.pipeline.CompareOp", "blaze3d.platform.CompareOp") }
            string(true) { replace("renderpearl.api.pipeline.BlendFactor", "blaze3d.platform.BlendFactor") }
            string(true) { replace("renderpearl.api.pipeline.UniformType", "blaze3d.shaders.UniformType") }
            string(true) { replace("renderpearl.api.pipeline.", "blaze3d.pipeline.") }
            regex(true) { replace("new (?:Identifier|ResourceLocation)\\(", "ResourceLocation.fromNamespaceAndPath(", "new (?:Identifier|ResourceLocation)\\(", "ResourceLocation.fromNamespaceAndPath(") }
            regex(true) { replace("(?:Identifier|ResourceLocation)\\.tryParse\\(", "ResourceLocation.parse(", "(?:Identifier|ResourceLocation)\\.tryParse\\(", "ResourceLocation.parse(") }
            regex(true) { replace("(?<!shaders\\.)\\bShaderSource\\b", "NativeShaderSource262", "(?<!shaders\\.)\\bShaderSource\\b", "NativeShaderSource262") }
            regex(true) { replace("\\.withUniform\\(([^,]+), UniformType.COMBINED_IMAGE_SAMPLER\\)", ".withSampler(\$1)", "\\.withUniform\\(([^,]+), UniformType.COMBINED_IMAGE_SAMPLER\\)", ".withSampler(\$1)") }
            regex(true) { replace("pass\\.setUniform\\(([^;]+), sampler\\);", "pass.bindTexture(\$1, sampler);", "pass\\.setUniform\\(([^;]+), sampler\\);", "pass.bindTexture(\$1, sampler);") }
        }
        if (current.version == "26.3") {
            string(true) { replace("ResourceLocation", "Identifier") }
            string(true) { replace(".getTimer()", ".getDeltaTracker()") }
            string(true) { replace(".getMainCamera().getPosition()", ".mainCamera().position()") }
            string(true) { replace(".mainCamera().getPosition()", ".mainCamera().position()") }
            string(true) { replace("GlStateManager.glShaderSource(shader, java.util.List.of(src))", "GlStateManager.glShaderSource(shader, src)") }
            string(true) { replace("level.chunk.ChunkStatus", "level.chunk.status.ChunkStatus") }
            string(true) { replace("blaze3d.platform.GlStateManager", "renderpearl.backend.opengl.GlStateManager") }
            string(true) { replace("blaze3d.opengl.", "renderpearl.backend.opengl.") }
            string(true) { replace("blaze3d.pipeline.BlendFunction", "renderpearl.api.pipeline.BlendFunction") }
            string(true) { replace("blaze3d.pipeline.ColorTargetState", "renderpearl.api.pipeline.ColorTargetState") }
            string(true) { replace("blaze3d.pipeline.DepthStencilState", "renderpearl.api.pipeline.DepthStencilState") }
            string(true) { replace("blaze3d.pipeline.RenderPipeline", "renderpearl.api.pipeline.RenderPipeline") }
            string(true) { replace("blaze3d.platform.CompareOp", "renderpearl.api.pipeline.CompareOp") }
            string(true) { replace("blaze3d.shaders.UniformType", "renderpearl.api.pipeline.UniformType") }
            string(true) { replace("blaze3d.buffers.GpuBufferSlice", "renderpearl.api.buffers.GpuBufferSlice") }
            string(true) { replace("blaze3d.textures.GpuTexture", "renderpearl.api.textures.GpuTexture") }
            string(true) { replace("blaze3d.vertex.VertexFormat", "renderpearl.api.vertex.VertexFormat") }
            string(true) { replace(".getMainRenderTarget()", ".gameRenderer.mainRenderTarget()") }
            string(true) { replace(".getMainCamera()", ".mainCamera()") }
            string(true) { replace(".getGameRenderState()", ".gameRenderState()") }
            string(true) { replace(".useDepth", ".hasDepth()") }
            string(true) { replace(".isZZeroToOne()", ".getDeviceInfo().isZZeroToOne()") }
            string(true) { replace("GlStateManager._enableBlend()", "GlStateManager._enableBlend(0)") }
            string(true) { replace("GlStateManager._disableBlend()", "GlStateManager._disableBlend(0)") }
            string(true) { replace("InputConstants.Type.KEYSYM", "InputConstants.Type.KEYBOARD") }
            string(true) { replace(".getOverlay()", ".gui.overlay()") }
            string(true) { replace(".setScreen(", ".gui.setScreen(") }
            string(true) { replace("mc.screen", "mc.gui.screen()") }
            string(true) { replace("minecraft.screen", "minecraft.gui.screen()") }
            string(true) { replace("Minecraft.getInstance().screen", "Minecraft.getInstance().gui.screen()") }
            regex(true) { replace("new (?:Identifier|ResourceLocation)\\(", "ResourceLocation.fromNamespaceAndPath(", "new (?:Identifier|ResourceLocation)\\(", "ResourceLocation.fromNamespaceAndPath(") }
            regex(true) { replace("(?:Identifier|ResourceLocation)\\.tryParse\\(", "ResourceLocation.parse(", "(?:Identifier|ResourceLocation)\\.tryParse\\(", "ResourceLocation.parse(") }
        }
        string(current.parsed >= "26.2") {
            replace("GlStateManager._enableBlend()", "GlStateManager._enableBlend(0)")
            replace("GlStateManager._disableBlend()", "GlStateManager._disableBlend(0)")
        }
        string(current.parsed >= "26.2") {
            replace(".getMainRenderTarget()", ".gameRenderer.mainRenderTarget()")
        }
    }
}

val launchTargets = mapOf(
    "1201" to "1.20.1",
    "1211" to "1.21.1",
    "12111" to "1.21.11",
    "2612" to "26.1.2",
    "262" to "26.2",
    "263" to "26.3",
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
