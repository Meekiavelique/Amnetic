package com.meekdev.amnetic.client.gbuffer.internal;

import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;
import java.util.Optional;
import java.util.OptionalDouble;

/** Reconstructs world normals from Minecraft's reversed depth without a GL framebuffer. */
final class NativeNormalFill implements AutoCloseable {
    private CompiledRenderPipeline pipeline;
    private GpuSampler sampler;
    private final java.nio.ByteBuffer data=BufferUtils.createByteBuffer(80);

    void render(CameraSnapshot camera) {
        if (camera == null) return;
        ensure();
        var gbuffer = GBufferTargets.INSTANCE;
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        data.clear();
        camera.invViewProj.get(0, data);
        data.position(64).putInt(camera.zeroToOne ? 1 : 0).putFloat(1).putLong(0).flip();
        var uniforms = encoder.transientMemory().uploadGpu(data, 256, GpuBuffer.USAGE_UNIFORM);
        var descriptor = RenderPassDescriptor.builder(() -> "Amnetic normal reconstruction")
                .withColorAttachment(target.getColorTextureView()).withColorAttachment(gbuffer.nativeColorView(0))
                .withColorAttachment(gbuffer.nativeColorView(1)).build();
        try (var pass = encoder.createRenderPass(descriptor)) {
            pass.setPipeline(pipeline);
            pass.setUniform("ReconstructParams", uniforms);
            pass.setUniform("DepthSampler", target.getDepthTextureView(), sampler);
            pass.draw(3, 1, 0, 0);
        }
        encoder.submit();
        gbuffer.setPopulated(true);
    }

    private void ensure() {
        if (pipeline != null) return;
        var id = Identifier.fromNamespaceAndPath("amnetic", "native/normal_fill");
        var definition = RenderPipeline.builder().withLocation(id).withVertexShader(id).withFragmentShader(id)
                .withBindGroupLayout(BindGroupLayout.builder()
                        .withUniform("ReconstructParams", UniformType.UNIFORM_BUFFER)
                        .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER).build())
                .withColorTargetState(0, new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE))
                .withColorTargetState(1, new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
                .withColorTargetState(2, new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,0)).withDepthStencilState(Optional.empty())
                .withCull(false).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
        String fragment = ShaderProgram.readSource(Identifier.fromNamespaceAndPath("amnetic", "shaders/gbuffer/normal_fill.fsh"))
                .replace("#version 410 core", "#version 450")
                .replace("in vec2 vUV;", "layout(location=0) in vec2 vUV;")
                .replace("uniform mat4 InvViewProj;", "layout(std140) uniform ReconstructParams { mat4 InvViewProj; int ZeroToOne; float DefaultRoughness; };")
                .replace("uniform int ZeroToOne;", "").replace("uniform float DefaultRoughness;", "")
                .replace("c >= 1.0", "c <= 0.0");
        try (ShaderSource sources = new ShaderSource() {
            @Override public String getShader(Identifier location, ShaderType type) {
                return type == ShaderType.FRAGMENT ? fragment : """
                        #version 450
                        layout(location=0) out vec2 vUV;
                        void main() {
                            vec2 p = vec2(float((gl_VertexIndex << 1) & 2), float(gl_VertexIndex & 2));
                            vUV = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                        }
                        """;
            }
            @Override public CachedIncludeSource getInclude(Identifier location) { return null; }
            @Override public void close() {}
        }) {
//? if >=26.3 {
pipeline = RenderSystem.getDevice().compilePipeline(definition, sources, Runnable::run).join().finishCompile();
//? } else {
/*pipeline=definition; if(!RenderSystem.getDevice().precompilePipeline(definition,sources).isValid())throw new IllegalStateException("Native shader compilation failed");
*///? }
}
        if (pipeline == null) throw new IllegalStateException("Could not compile native normal reconstruction");
        try {
            sampler = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
        } catch (RuntimeException failure) { close(); throw failure; }
    }

    @Override public void close() {
        if (sampler != null) { sampler.close(); sampler = null; }
        if (pipeline != null) {
//? if >=26.3 {
            pipeline.close();
            //? }
pipeline = null; }
    }
}
