package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.light.LightSettings;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class VolumetricPass {

    public static final VolumetricPass INSTANCE = new VolumetricPass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/light/deferred.vsh");
    private static final Identifier TEMPORAL_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/volumetric/temporal.fsh");
    private static final Identifier UPSCALE_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/volumetric/upscale.fsh");
    private static final float FEEDBACK = 0.9f;

    private ShaderProgram temporal;
    private ShaderProgram upscale;
    private Framebuffer capture; // full-res scene depth
    private Framebuffer raw, historyA, historyB; // half-res scatter + ping-pong history
    private boolean usingA;
    private float scale = -1f;
    private boolean initialized;

    private final Matrix4f prevViewProj = new Matrix4f();
    private double prevEyeX, prevEyeY, prevEyeZ;
    private boolean historyValid;

    private VolumetricPass() {}

    public void render() {
        LightSettings ls = LightSettings.defaults();
        if (!ls.volumetric() || LightRegistry.INSTANCE.isEmpty()) { historyValid = false; return; }
        CameraSnapshot cam = CameraSnapshot.current();
        if (cam == null) return;

        ensure(ls.volumetricScale());
        capture.blitDepthFromMain();

        DeferredLightingPass.INSTANCE.renderVolumetricRaw(raw);

        try {
            GlState.beginFullscreen();

            Framebuffer resolved = raw;
            if (ls.volumetricTemporal()) {
                Framebuffer hPrev = usingA ? historyA : historyB;
                Framebuffer hCurr = usingA ? historyB : historyA;
                hCurr.begin();
                GlState.bindTexture(0, raw.colorTextureGlId(0));
                GlState.bindTexture(1, capture.depthTextureGlId());
                GlState.bindTexture(2, (historyValid ? hPrev : raw).colorTextureGlId(0));
                temporal.begin();
                temporal.setSampler("Raw", 0);
                temporal.setSampler("DepthSampler", 1);
                temporal.setSampler("History", 2);
                temporal.setMatrix4("PrevViewProj", prevViewProj);
                temporal.setMatrix4("InvViewProj", cam.invViewProj);
                temporal.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
                temporal.setVec3("EyeDelta",
                        (float) (cam.eye.x - prevEyeX), (float) (cam.eye.y - prevEyeY), (float) (cam.eye.z - prevEyeZ));
                temporal.setFloat("Feedback", historyValid ? FEEDBACK : 0f);
                temporal.draw();
                hCurr.end();
                resolved = hCurr;
                usingA = !usingA;
                historyValid = true;
            } else {
                historyValid = false;
            }

            int prevFbo = MainTargetFramebuffer.bind();
            try {
                GlState.bindTexture(0, resolved.colorTextureGlId(0));
                GlState.bindTexture(1, capture.depthTextureGlId());
                GlStateManager._enableBlend();
                GL11.glEnable(GL11.GL_BLEND);
                GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
                GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
                upscale.begin();
                upscale.setSampler("Scatter", 0);
                upscale.setSampler("DepthSampler", 1);
                upscale.draw();
            } finally {
                MainTargetFramebuffer.restore(prevFbo);
            }
        } finally {
            GlState.endFullscreen();
        }

        prevViewProj.set(cam.viewProj);
        prevEyeX = cam.eye.x; prevEyeY = cam.eye.y; prevEyeZ = cam.eye.z;
    }

    private void ensure(float s) {
        if (!initialized) {
            temporal = new ShaderProgram(VSH, TEMPORAL_FSH);
            upscale = new ShaderProgram(VSH, UPSCALE_FSH);
            capture = Framebuffers.screen("Volumetric Capture", FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build());
            initialized = true;
        }
        if (raw != null && scale == s) return;
        if (raw != null) raw.dispose();
        if (historyA != null) historyA.dispose();
        if (historyB != null) historyB.dispose();
        raw = Framebuffers.screen("Volumetric Raw", s, FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
        historyA = Framebuffers.screen("Volumetric History A", s, FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
        historyB = Framebuffers.screen("Volumetric History B", s, FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
        scale = s;
        historyValid = false;
    }

    public void dispose() {
        if (temporal != null) {
            temporal.close(); temporal = null;
        }
        if (upscale != null) {
            upscale.close();
            upscale = null;
        }
        if (capture != null) {
            capture.dispose();
            capture = null;
        }
        if (raw != null) {
            raw.dispose();
            raw = null;
        }
        if (historyA != null) {
            historyA.dispose();
            historyA = null;
        }
        if (historyB != null) {
            historyB.dispose();
            historyB = null;
        }
        scale = -1f;
        initialized = false;
        historyValid = false;
    }
}
