package com.meekdev.amnetic.client.ssao.internal;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.ssao.SsaoSettings;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

final class NativeSsaoPass implements AutoCloseable {
    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/native/ssao.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/native/ssao.fsh");
    private static final Identifier COMPOSITE_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/native/composite.fsh");
    private static final Identifier TEMPORAL_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/native/temporal.fsh");
    private static final Identifier BLUR_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/native/blur.fsh");

    private Framebuffer ao;
    private Framebuffer blurred;
    private Framebuffer historyA, historyB;
    private boolean usingA;
    private float aoScale = -1f;

    private final Matrix4f prevViewProj = new Matrix4f();
    private double prevEyeX, prevEyeY, prevEyeZ;
    private boolean historyValid;
    private int frame;
    private com.meekdev.amnetic.client.render.NativeScreenProgram nativeAo, nativeBlur, nativeTemporal, nativeComposite;
    private int nativeWidth, nativeHeight;

    private com.meekdev.amnetic.client.render.NativeScreenProgram nativeProgram(Identifier id) {
        return new com.meekdev.amnetic.client.render.NativeScreenProgram(id,
                source -> source.replaceAll("\\b(depth|cd|sd|d) >= 1\\.0", "$1 <= 0.0"),
                id.equals(FSH) ? new com.mojang.renderpearl.api.pipeline.ColorTargetState[]{
                        com.mojang.renderpearl.api.pipeline.ColorTargetState.DEFAULT,
                        new com.mojang.renderpearl.api.pipeline.ColorTargetState(java.util.Optional.empty(),com.mojang.renderpearl.api.GpuFormat.R32_FLOAT,15)}
                        : new com.mojang.renderpearl.api.pipeline.ColorTargetState[]{com.mojang.renderpearl.api.pipeline.ColorTargetState.DEFAULT});
    }

    void render(CameraSnapshot cam, SsaoSettings settings) {
        var s = settings;
        ensureBuffers(s.scale());
        var depth = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView();
        if (nativeWidth != ao.width() || nativeHeight != ao.height()) historyValid = false;
        nativeWidth = ao.width(); nativeHeight = ao.height();
        boolean hasNormals = GBufferTargets.INSTANCE.isPopulated();
        var normals = hasNormals ? GBufferTargets.INSTANCE.nativeColorView(0) : depth;
        if (nativeAo == null) nativeAo = nativeProgram(FSH);
        if (nativeBlur == null) nativeBlur = nativeProgram(BLUR_FSH);
        if (nativeTemporal == null) nativeTemporal = nativeProgram(TEMPORAL_FSH);
        if (nativeComposite == null) nativeComposite = new com.meekdev.amnetic.client.render.NativeScreenProgram(COMPOSITE_FSH,
                new com.mojang.renderpearl.api.pipeline.ColorTargetState(new com.mojang.renderpearl.api.pipeline.BlendFunction(
                        com.mojang.renderpearl.api.pipeline.BlendFactor.ZERO, com.mojang.renderpearl.api.pipeline.BlendFactor.SRC_COLOR,
                        com.mojang.renderpearl.api.pipeline.BlendFactor.ZERO, com.mojang.renderpearl.api.pipeline.BlendFactor.ONE)));
        for (var p : new com.meekdev.amnetic.client.render.NativeScreenProgram[]{nativeAo, nativeBlur}) {
            p.setTexture("DepthSampler", depth);
            p.setTexture("GNormalSampler", normals);
            p.setInt("HasGBuffer", hasNormals ? 1 : 0);
            p.setMatrix("InvViewProj", cam.invViewProj);
            p.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        }
        nativeAo.setMatrix("ViewProj", cam.viewProj);
        nativeAo.setMatrix("View", cam.view);
        nativeAo.setFloat("Radius", s.radius()); nativeAo.setFloat("Intensity", s.intensity());
        nativeAo.setFloat("Bias", s.bias()); nativeAo.setFloat("Power", s.power());
        nativeAo.setInt("Frame", s.temporal() ? frame : 0);
        nativeAo.draw(ao.renderPassDescriptor());
        nativeBlur.setTexture("AoSampler", ao.colorTextureView(0));
        nativeBlur.setTexture("DistanceSampler",ao.colorTextureView(1));
        nativeBlur.setInt("PreparedDistance",1);
        nativeBlur.draw(blurred.renderPassDescriptor());
        Framebuffer resolved = blurred;
        if (s.temporal()) {
            var previous = usingA ? historyA : historyB;
            var current = usingA ? historyB : historyA;
            nativeTemporal.setTexture("AoRaw", blurred.colorTextureView(0));
            nativeTemporal.setTexture("DepthSampler", depth);
            nativeTemporal.setTexture("History", historyValid && previous.isAllocated() ? previous.colorTextureView(0) : blurred.colorTextureView(0));
            nativeTemporal.setMatrix("PrevViewProj", prevViewProj);
            nativeTemporal.setMatrix("InvViewProj", cam.invViewProj);
            nativeTemporal.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
            nativeTemporal.setVector("EyeDelta", (float) (cam.eye.x - prevEyeX), (float) (cam.eye.y - prevEyeY), (float) (cam.eye.z - prevEyeZ));
            nativeTemporal.setFloat("Feedback", historyValid ? s.feedback() : 0);
            nativeTemporal.draw(current.renderPassDescriptor());
            resolved = current; usingA = !usingA; historyValid = true;
        } else historyValid = false;
        prevViewProj.set(cam.viewProj);
        prevEyeX = cam.eye.x; prevEyeY = cam.eye.y; prevEyeZ = cam.eye.z;
        frame = (frame + 1) & 1023;
        nativeComposite.setTexture("AoSampler", resolved.colorTextureView(0));
        nativeComposite.setTexture("DepthSampler",depth);
        nativeComposite.setTexture("DistanceSampler",ao.colorTextureView(1));
        nativeComposite.setMatrix("InvViewProj",cam.invViewProj);
        nativeComposite.setInt("ZeroToOne",cam.zeroToOne?1:0);
        nativeComposite.setInt("EdgeAware",ao.width()!=net.minecraft.client.Minecraft.getInstance().gameRenderer.mainRenderTarget().width?1:0);
        nativeComposite.draw(com.mojang.renderpearl.api.commands.RenderPassDescriptor.builder(() -> "Amnetic SSAO composite")
                .withColorAttachment(net.minecraft.client.Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView()).build());
    }

    private void ensureBuffers(float scale) {
        if (ao != null && aoScale == scale) return;
        freeBuffers();
        ao = Framebuffers.screen("SSAO Occlusion", scale, FramebufferSpec.builder().color(ColorFormat.RGBA8).color(ColorFormat.R32F).build());
        blurred = Framebuffers.screen("SSAO Blur", scale, FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
        historyA = Framebuffers.screen("SSAO History A", scale, FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
        historyB = Framebuffers.screen("SSAO History B", scale, FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
        aoScale = scale;
        historyValid = false;
    }
    private void freeBuffers() {
        if (ao != null) { ao.dispose(); ao = null; }
        if (blurred != null) { blurred.dispose(); blurred = null; }
        if (historyA != null) { historyA.dispose(); historyA = null; }
        if (historyB != null) { historyB.dispose(); historyB = null; }
    }
    @Override public void close() {
        for (var program : new com.meekdev.amnetic.client.render.NativeScreenProgram[]{nativeAo, nativeBlur, nativeTemporal, nativeComposite}) {
            if (program != null) program.close();
        }
        nativeAo = nativeBlur = nativeTemporal = nativeComposite = null;
        nativeWidth = nativeHeight = 0;
        freeBuffers();
        aoScale = -1;
        historyValid = false;
    }
}
