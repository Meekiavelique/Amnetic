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
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class SsaoPass extends ScreenPass {

    public static final SsaoPass INSTANCE = new SsaoPass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/ssao.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/ssao.fsh");
    private static final Identifier COMPOSITE_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/composite.fsh");
    private static final Identifier TEMPORAL_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/temporal.fsh");
    private static final Identifier BLUR_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/blur.fsh");

    private ShaderProgram composite;
    private ShaderProgram temporal;
    private ShaderProgram blur;
    private Framebuffer capture;
    private Framebuffer ao;
    private Framebuffer blurred;
    private Framebuffer historyA, historyB;
    private boolean usingA;
    private float aoScale = -1f;
    private SsaoSettings settings;

    private final Matrix4f prevViewProj = new Matrix4f();
    private double prevEyeX, prevEyeY, prevEyeZ;
    private boolean historyValid;
    private int frame;

    private SsaoPass() { super("SSAO"); }

    public void render(SsaoSettings s) {
        this.settings = s;
        dispatch();
    }

    @Override protected boolean enabled() { return settings != null && settings.isEnabled(); }

    @Override
    protected ShaderProgram createProgram() {
        composite = new ShaderProgram(VSH, COMPOSITE_FSH);
        temporal = new ShaderProgram(VSH, TEMPORAL_FSH);
        blur = new ShaderProgram(VSH, BLUR_FSH);
        return new ShaderProgram(VSH, FSH);
    }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram ssao) {
        SsaoSettings s = settings;
        ensureBuffers(s.scale());

        capture.blitDepthFromMain();

        ao.begin();
        boolean hasGBuffer = GBufferTargets.INSTANCE.isPopulated();
        GlState.bindTexture(1, capture.depthTextureGlId());
        GlState.bindTexture(2, hasGBuffer ? GBufferTargets.INSTANCE.normalGlId() : capture.depthTextureGlId());
        ssao.begin();
        ssao.setSampler("DepthSampler", 1);
        ssao.setSampler("GNormalSampler", 2);
        ssao.setInt("HasGBuffer", hasGBuffer ? 1 : 0);
        ssao.setMatrix4("ViewProj", cam.viewProj);
        ssao.setMatrix4("InvViewProj", cam.invViewProj);
        ssao.setMatrix4("View", cam.view);
        ssao.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        ssao.setFloat("Radius", s.radius());
        ssao.setFloat("Intensity", s.intensity());
        ssao.setFloat("Bias", s.bias());
        ssao.setFloat("Power", s.power());
        // advance the kernel rotation per frame only when temporal accumulation can integrate it,
        // held at 0 otherwise so the pattern stays stable and doesn't flicker
        ssao.setInt("Frame", s.temporal() ? frame : 0);
        ssao.draw();
        ao.end();

        // 7x7 normal-aware bilateral blur to remove the per-pixel rotation pattern without smearing
        // across silhouettes; the normal weight is what stops corner sparkle where a depth-only test starves.
        blurred.begin();
        GlState.bindTexture(0, ao.colorTextureGlId(0));
        GlState.bindTexture(1, capture.depthTextureGlId());
        GlState.bindTexture(2, hasGBuffer ? GBufferTargets.INSTANCE.normalGlId() : capture.depthTextureGlId());
        blur.begin();
        blur.setSampler("AoSampler", 0);
        blur.setSampler("DepthSampler", 1);
        blur.setSampler("GNormalSampler", 2);
        blur.setInt("HasGBuffer", hasGBuffer ? 1 : 0);
        blur.setMatrix4("InvViewProj", cam.invViewProj);
        blur.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        blur.draw();
        blurred.end();

        Framebuffer resolved = blurred;
        if (s.temporal()) {
            Framebuffer hPrev = usingA ? historyA : historyB;
            Framebuffer hCurr = usingA ? historyB : historyA;
            hCurr.begin();
            GlState.bindTexture(0, blurred.colorTextureGlId(0));
            GlState.bindTexture(1, capture.depthTextureGlId());
            // hPrev isn't allocated yet on the first temporal frame, fall back to the raw AO buffer
            GlState.bindTexture(2, (historyValid && hPrev.isAllocated() ? hPrev : blurred).colorTextureGlId(0));
            temporal.begin();
            temporal.setSampler("AoRaw", 0);
            temporal.setSampler("DepthSampler", 1);
            temporal.setSampler("History", 2);
            temporal.setMatrix4("PrevViewProj", prevViewProj);
            temporal.setMatrix4("InvViewProj", cam.invViewProj);
            temporal.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
            temporal.setVec3("EyeDelta",
                    (float) (cam.eye.x - prevEyeX), (float) (cam.eye.y - prevEyeY), (float) (cam.eye.z - prevEyeZ));
            temporal.setFloat("Feedback", historyValid ? s.feedback() : 0f);
            temporal.draw();
            hCurr.end();
            resolved = hCurr;
            usingA = !usingA;
            historyValid = true;
        } else {
            historyValid = false;
        }
        prevViewProj.set(cam.viewProj);
        prevEyeX = cam.eye.x; prevEyeY = cam.eye.y; prevEyeZ = cam.eye.z;
        // wrap well within float's exact-integer range so fract(Frame * 0.618) stays precise
        frame = (frame + 1) & 1023;

        int prevFbo = MainTargetFramebuffer.bind();
        try {
            GlState.bindTexture(0, resolved.colorTextureGlId(0));
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL11.GL_ZERO, GL11.GL_SRC_COLOR, GL11.GL_ZERO, GL11.GL_ONE);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_ZERO, GL11.GL_SRC_COLOR, GL11.GL_ZERO, GL11.GL_ONE);
            composite.begin();
            composite.setSampler("AoSampler", 0);
            composite.draw();
        } finally {
            MainTargetFramebuffer.restore(prevFbo);
        }
        return true;
    }

    @Override
    protected void onDispose() {
        if (composite != null) { composite.close(); composite = null; }
        if (temporal != null) { temporal.close(); temporal = null; }
        if (blur != null) { blur.close(); blur = null; }
        if (capture != null) { capture.dispose(); capture = null; }
        if (ao != null) { ao.dispose(); ao = null; }
        if (blurred != null) { blurred.dispose(); blurred = null; }
        if (historyA != null) { historyA.dispose(); historyA = null; }
        if (historyB != null) { historyB.dispose(); historyB = null; }
        aoScale = -1f;
        historyValid = false;
    }

    private void ensureBuffers(float scale) {
        if (ao != null && aoScale == scale) return;
        if (capture != null) capture.dispose();
        if (ao != null) ao.dispose();
        if (blurred != null) blurred.dispose();
        if (historyA != null) historyA.dispose();
        if (historyB != null) historyB.dispose();
        // depth matched to the AO trace's own scale so a lowered SSAO resolution also saves
        // the blit cost, instead of always blitting a full-res depth copy
        capture = Framebuffers.screen("SSAO Capture", scale, FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build());
        ao = Framebuffers.screen("SSAO Occlusion", scale, FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
        blurred = Framebuffers.screen("SSAO Blur", scale, FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
        historyA = Framebuffers.screen("SSAO History A", scale, FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
        historyB = Framebuffers.screen("SSAO History B", scale, FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
        aoScale = scale;
        historyValid = false;
    }
}
