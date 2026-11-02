package com.meekdev.amnetic.client.grade.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.internal.NativeFramebuffer;
import com.meekdev.amnetic.client.grade.ColorGradeSettings;
import com.meekdev.amnetic.client.render.ImportedTextures;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

final class NativeColorGrade implements AutoCloseable {
    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("amnetic", "native/colorgrade_vertex");
    private static final Identifier FRAGMENT = Identifier.fromNamespaceAndPath("amnetic", "native/colorgrade_fragment");
    private static final Identifier FRAGMENT_SOURCE = Identifier.fromNamespaceAndPath("amnetic", "shaders/grade/native/colorgrade.fsh");
    private static final int PARAMETER_BYTES = 48;
    private static final Identifier VERTEX_SOURCE = Identifier.fromNamespaceAndPath("amnetic", "shaders/native/screen.vsh");
    private final NativeFramebuffer capture = new NativeFramebuffer(
            FramebufferSpec.builder().color(ColorFormat.RGBA8).build(), "Amnetic grade snapshot");
    private final Set<Identifier> loadedLuts = new HashSet<>();
    private final ByteBuffer parameters = BufferUtils.createByteBuffer(PARAMETER_BYTES);
    private CompiledRenderPipeline pipeline;
    private GpuBuffer uniforms;
    private GpuSampler sampler;

    void render(ColorGradeSettings settings) {
        var mc = Minecraft.getInstance();
        var target = mc.gameRenderer.mainRenderTarget();
        var device = RenderSystem.getDevice();
        ensure();
        capture.allocate(target.width, target.height);
        GpuTextureView lut = capture.colorView(0);
        boolean hasLut = false;
        if (settings.lut() != null) {
            var textures = mc.getTextureManager();
            if (!ImportedTextures.isImported(settings.lut()) && loadedLuts.add(settings.lut())) {
                VanillaCompat.loadTexture(textures, settings.lut());
            }
            var texture = textures.getTexture(settings.lut());
            if (texture != null && texture.getTextureView() != null) {
                lut = texture.getTextureView();
                hasLut = true;
            }
        }
        parameters.clear();
        parameters.putInt(hasLut ? 1 : 0).putFloat(settings.lutSize()).putFloat(settings.lutIntensity())
                .putFloat(settings.exposure()).putFloat(settings.contrast()).putFloat(settings.saturation())
                .putFloat(settings.brightness()).putFloat(settings.temperature()).putFloat(settings.tint())
                .putFloat(settings.gamma()).putLong(0).flip();
        var encoder = device.createCommandEncoder();
        encoder.copyTextureToTexture(target.getColorTexture(), capture.color(0), 0, 0, 0, 0, 0, target.width, target.height);
        encoder.writeToBuffer(uniforms.slice(), parameters);
        try (var pass = encoder.createRenderPass(() -> "Amnetic color grade", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(pipeline);
            pass.setUniform("GradeParams", uniforms);
            pass.setUniform("ColorSampler", capture.colorView(0), sampler);
            pass.setUniform("LutSampler", lut, sampler);
            pass.draw(3, 1, 0, 0);
        }
        encoder.submit();
    }

    private void ensure() {
        if (pipeline != null) return;
        var device = RenderSystem.getDevice();
        var definition = RenderPipeline.builder().withLocation(FRAGMENT)
                .withVertexShader(VERTEX).withFragmentShader(FRAGMENT)
                .withBindGroupLayout(BindGroupLayout.builder()
                        .withUniform("GradeParams", UniformType.UNIFORM_BUFFER)
                        .withUniform("ColorSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                        .withUniform("LutSampler", UniformType.COMBINED_IMAGE_SAMPLER).build())
                .withColorTargetState(ColorTargetState.DEFAULT).withDepthStencilState(Optional.empty())
                .withCull(false).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
        String fragmentSource = ShaderProgram.readSource(FRAGMENT_SOURCE);
        String vertexSource = ShaderProgram.readSource(VERTEX_SOURCE);
        try (ShaderSource sources = new ShaderSource() {
            @Override public String getShader(Identifier id, ShaderType type) {
                return type == ShaderType.FRAGMENT ? fragmentSource : vertexSource;
            }
            @Override public CachedIncludeSource getInclude(Identifier id) { return null; }
            @Override public void close() {}
        }) {
            //? if >=26.3 {
            pipeline = device.compilePipeline(definition, sources, Runnable::run).join().finishCompile();
            //? } else {
            /*pipeline = definition;
            if (!device.precompilePipeline(definition, sources).isValid()) throw new IllegalStateException("Native shader compilation failed");
            *///? }
        }
        if (pipeline == null) throw new IllegalStateException("Could not compile native color grade pipeline");
        try {
            uniforms = device.createBuffer(() -> "Amnetic grade uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, PARAMETER_BYTES);
            sampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    @Override public void close() {
        capture.close();
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
        if (sampler != null) {
            sampler.close();
            sampler = null;
        }
        if (pipeline != null) {
            //? if >=26.3 {
            pipeline.close();
            //? }
            pipeline = null;
        }
        loadedLuts.clear();
    }
}
