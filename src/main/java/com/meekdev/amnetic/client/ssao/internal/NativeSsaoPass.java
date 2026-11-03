package com.meekdev.amnetic.client.ssao.internal;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.NativeScreenProgram;
import com.meekdev.amnetic.client.ssao.SsaoSettings;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import java.util.Optional;

final class NativeSsaoPass implements AutoCloseable {
    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/native/ssao.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/native/ssao.fsh");
    private static final Identifier COMPOSITE_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/native/composite.fsh");
    private static final Identifier TEMPORAL_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/native/temporal.fsh");
    private static final Identifier BLUR_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/native/blur.fsh");
    private static final int WRITE_ALL = 15;
    private static final int FRAME_MASK = 1023;

    private Framebuffer ao;
    private Framebuffer blurred;
    private Framebuffer historyA;
    private Framebuffer historyB;
    private boolean usingA;
    private float aoScale = -1f;

    private final Matrix4f prevViewProj = new Matrix4f();
    private double prevEyeX;
    private double prevEyeY;
    private double prevEyeZ;
    private boolean historyValid;
    private int frame;
    private NativeScreenProgram nativeAo;
    private NativeScreenProgram nativeBlur;
    private NativeScreenProgram nativeTemporal;
    private NativeScreenProgram nativeComposite;
    private int nativeWidth;
    private int nativeHeight;

    private NativeScreenProgram nativeProgram(Identifier id) {
        return new NativeScreenProgram(id, id.equals(FSH) ? occlusionTargets() : new ColorTargetState[]{ColorTargetState.DEFAULT});
    }

    private static ColorTargetState[] occlusionTargets() {
        return new ColorTargetState[]{
                ColorTargetState.DEFAULT,
                new ColorTargetState(Optional.empty(), GpuFormat.R32_FLOAT, WRITE_ALL)
        };
    }

    void render(CameraSnapshot cam, SsaoSettings settings) {
        var s = settings;
        ensureBuffers(s.scale());
        var depth = Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView();
        if (nativeWidth != ao.width() || nativeHeight != ao.height()) historyValid = false;
        nativeWidth = ao.width();
        nativeHeight = ao.height();
        boolean hasNormals = GBufferTargets.INSTANCE.isPopulated();
        var normals = hasNormals ? GBufferTargets.INSTANCE.nativeColorView(0) : depth;
        ensurePrograms();
        for (var p : new NativeScreenProgram[]{nativeAo, nativeBlur}) {
            p.setTexture("DepthSampler", depth);
            p.setTexture("GNormalSampler", normals);
            p.setInt("HasGBuffer", hasNormals ? 1 : 0);
            p.setMatrix("InvViewProj", cam.invViewProj);
            p.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        }
        nativeAo.setMatrix("ViewProj", cam.viewProj);
        nativeAo.setMatrix("View", cam.view);
        nativeAo.setFloat("Radius", s.radius());
        nativeAo.setFloat("Intensity", s.intensity());
        nativeAo.setFloat("Bias", s.bias());
        nativeAo.setFloat("Power", s.power());
        nativeAo.setInt("Frame", s.temporal() ? frame : 0);
        nativeAo.draw(ao.renderPassDescriptor());
        nativeBlur.setTexture("AoSampler", ao.colorTextureView(0));
        nativeBlur.setTexture("DistanceSampler", ao.colorTextureView(1));
        nativeBlur.setInt("PreparedDistance", 1);
        nativeBlur.draw(blurred.renderPassDescriptor());
        Framebuffer resolved = blurred;
        if (s.temporal()) {
            resolved = resolveTemporal(cam, s, depth);
        } else {
            historyValid = false;
        }
        prevViewProj.set(cam.viewProj);
        prevEyeX = cam.eye.x;
        prevEyeY = cam.eye.y;
        prevEyeZ = cam.eye.z;
        frame = (frame + 1) & FRAME_MASK;
        composite(cam, resolved, depth);
    }

    private void ensurePrograms() {
        if (nativeAo == null) nativeAo = nativeProgram(FSH);
        if (nativeBlur == null) nativeBlur = nativeProgram(BLUR_FSH);
        if (nativeTemporal == null) nativeTemporal = nativeProgram(TEMPORAL_FSH);
        if (nativeComposite == null) {
            nativeComposite = new NativeScreenProgram(COMPOSITE_FSH,
                    new ColorTargetState(new BlendFunction(
                            BlendFactor.ZERO, BlendFactor.SRC_COLOR,
                            BlendFactor.ZERO, BlendFactor.ONE)));
        }
    }

    private Framebuffer resolveTemporal(CameraSnapshot cam, SsaoSettings s, GpuTextureView depth) {
        var previous = usingA ? historyA : historyB;
        var current = usingA ? historyB : historyA;
        boolean usePrevious = historyValid && previous.isAllocated();
        nativeTemporal.setTexture("AoRaw", blurred.colorTextureView(0));
        nativeTemporal.setTexture("DepthSampler", depth);
        nativeTemporal.setTexture("History", usePrevious ? previous.colorTextureView(0) : blurred.colorTextureView(0));
        nativeTemporal.setMatrix("PrevViewProj", prevViewProj);
        nativeTemporal.setMatrix("InvViewProj", cam.invViewProj);
        nativeTemporal.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        float x = (float) (cam.eye.x - prevEyeX);
        float y = (float) (cam.eye.y - prevEyeY);
        float z = (float) (cam.eye.z - prevEyeZ);
        nativeTemporal.setVector("EyeDelta", x, y, z);
        nativeTemporal.setFloat("Feedback", historyValid ? s.feedback() : 0);
        nativeTemporal.draw(current.renderPassDescriptor());
        usingA = !usingA;
        historyValid = true;
        return current;
    }

    private void composite(CameraSnapshot cam, Framebuffer resolved, GpuTextureView depth) {
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        nativeComposite.setTexture("AoSampler", resolved.colorTextureView(0));
        nativeComposite.setTexture("DepthSampler", depth);
        nativeComposite.setTexture("DistanceSampler", ao.colorTextureView(1));
        nativeComposite.setMatrix("InvViewProj", cam.invViewProj);
        nativeComposite.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        nativeComposite.setInt("EdgeAware", ao.width() != main.width ? 1 : 0);
        nativeComposite.draw(RenderPassDescriptor.builder(() -> "Amnetic SSAO composite")
                .withColorAttachment(main.getColorTextureView()).build());
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
        if (ao != null) ao.dispose();
        ao = null;
        if (blurred != null) blurred.dispose();
        blurred = null;
        if (historyA != null) historyA.dispose();
        historyA = null;
        if (historyB != null) historyB.dispose();
        historyB = null;
    }

    @Override public void close() {
        for (var program : new NativeScreenProgram[]{nativeAo, nativeBlur, nativeTemporal, nativeComposite}) {
            if (program != null) program.close();
        }
        nativeAo = null;
        nativeBlur = null;
        nativeTemporal = null;
        nativeComposite = null;
        nativeWidth = 0;
        nativeHeight = 0;
        freeBuffers();
        aoScale = -1;
        historyValid = false;
    }
}
