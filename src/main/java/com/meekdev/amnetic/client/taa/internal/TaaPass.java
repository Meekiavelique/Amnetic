package com.meekdev.amnetic.client.taa.internal;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.taa.TaaSettings;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public final class TaaPass extends ScreenPass {

    public static final TaaPass INSTANCE = new TaaPass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/fullscreen.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/taa/taa.fsh");
    private static final Identifier BLIT_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/taa/blit.fsh");

    // a camera cut (teleport, dimension change) larger than this invalidates the history,
    // reprojecting across it would smear the old view over the new one
    private static final double MAX_EYE_JUMP = 16.0;

    private ShaderProgram blit;
    private Framebuffer capture; // full-res scene colour + depth
    private Framebuffer historyA, historyB;
    private boolean usingA;
    private TaaSettings settings;

    private final Matrix4f curViewProj = new Matrix4f();
    private final Matrix4f curInvViewProj = new Matrix4f();
    private final Matrix4f prevViewProj = new Matrix4f();
    private double prevEyeX, prevEyeY, prevEyeZ;
    private boolean historyValid;

    private TaaPass() { super("TAA"); }

    public void render(TaaSettings s) {
        this.settings = s;
        if (!s.isEnabled()) historyValid = false; // don't reproject stale frames after a re-enable
        dispatch();
    }

    @Override protected boolean enabled() { return settings != null && settings.isEnabled(); }

    @Override
    protected ShaderProgram createProgram() {
        blit = new ShaderProgram(VSH, BLIT_FSH);
        capture = Framebuffers.screen("TAA Capture",
                FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build());
        historyA = Framebuffers.screen("TAA History A",
                FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
        historyB = Framebuffers.screen("TAA History B",
                FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
        return new ShaderProgram(VSH, FSH);
    }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram resolve) {
        capture.blitColorFromMain();
        capture.blitDepthFromMain();

        // reprojection uses unjittered matrices on both ends, the jitter is a sampling
        // signal inside the frame, not camera motion
        TaaJitter.INSTANCE.unjitter(cam.viewProj, curViewProj);
        curInvViewProj.set(curViewProj).invert();

        double dx = cam.eye.x - prevEyeX, dy = cam.eye.y - prevEyeY, dz = cam.eye.z - prevEyeZ;
        boolean cut = (dx * dx + dy * dy + dz * dz) > MAX_EYE_JUMP * MAX_EYE_JUMP;

        Framebuffer hPrev = usingA ? historyA : historyB;
        Framebuffer hCurr = usingA ? historyB : historyA;

        hCurr.begin();
        GlState.bindTexture(0, capture.colorTextureGlId(0));
        GlState.bindTexture(1, (historyValid && hPrev.isAllocated() ? hPrev : capture).colorTextureGlId(0));
        GlState.bindTexture(2, capture.depthTextureGlId());
        resolve.begin();
        resolve.setSampler("ColorSampler", 0);
        resolve.setSampler("History", 1);
        resolve.setSampler("DepthSampler", 2);
        resolve.setMatrix4("PrevViewProj", prevViewProj);
        resolve.setMatrix4("InvViewProj", curInvViewProj);
        resolve.setVec3("EyeDelta", (float) dx, (float) dy, (float) dz);
        resolve.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        resolve.setFloat("Feedback", historyValid && !cut ? settings.feedback() : 0f);
        resolve.draw();
        hCurr.end();

        int prevFbo = MainTargetFramebuffer.bind();
        try {
            GlStateManager._disableBlend();
            GlState.bindTexture(0, hCurr.colorTextureGlId(0));
            blit.begin();
            blit.setSampler("ColorSampler", 0);
            blit.draw();
        } finally {
            MainTargetFramebuffer.restore(prevFbo);
        }

        usingA = !usingA;
        historyValid = true;
        prevViewProj.set(curViewProj);
        prevEyeX = cam.eye.x; prevEyeY = cam.eye.y; prevEyeZ = cam.eye.z;
        return true;
    }

    @Override
    protected void onDispose() {
        if (blit != null) { blit.close(); blit = null; }
        if (capture != null) { capture.dispose(); capture = null; }
        if (historyA != null) { historyA.dispose(); historyA = null; }
        if (historyB != null) { historyB.dispose(); historyB = null; }
        historyValid = false;
    }
}
