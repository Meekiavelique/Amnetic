package com.meekdev.amnetic.client.ssr.internal;

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
import com.meekdev.amnetic.client.ssr.SsrSettings;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public final class SsrPass extends ScreenPass {

    public static final SsrPass INSTANCE = new SsrPass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssr/ssr.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssr/ssr.fsh");
    private static final Identifier COMPOSITE_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssr/composite.fsh");
    private static final Identifier TEMPORAL_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssr/temporal.fsh");

    private ShaderProgram composite;
    private ShaderProgram temporal;
    private Framebuffer capture; // full-res scene color + depth
    private Framebuffer reflection; // scaled reflection result (rgb = color, a = strength)
    private Framebuffer historyA, historyB;
    private boolean usingA;
    private float reflectionScale = -1f;
    private SsrSettings settings;

    private final Matrix4f prevViewProj = new Matrix4f();
    private double prevEyeX, prevEyeY, prevEyeZ;
    private boolean historyValid;
    private long frameCounter;

    private SsrPass() { super("SSR"); }

    public void render(SsrSettings s) {
        this.settings = s;
        dispatch();
    }

    @Override protected boolean enabled() { return settings != null && settings.isEnabled(); }

    @Override
    protected ShaderProgram createProgram() {
        composite = new ShaderProgram(VSH, COMPOSITE_FSH);
        temporal = new ShaderProgram(VSH, TEMPORAL_FSH);
        capture = Framebuffers.screen("SSR Capture", FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build());
        return new ShaderProgram(VSH, FSH); // the ray-march program
    }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram march) {
        SsrSettings s = settings;
        ensureReflection(s.resolution());

        capture.blitColorFromMain();
        capture.blitDepthFromMain();

        reflection.begin();
        GlState.bindTexture(0, capture.colorTextureGlId(0));
        GlState.bindTexture(1, capture.depthTextureGlId());
        boolean hasGBuffer = GBufferTargets.INSTANCE.isPopulated();
        if (hasGBuffer) {
            GlState.bindTexture(2, GBufferTargets.INSTANCE.normalGlId());
            GlState.bindTexture(3, GBufferTargets.INSTANCE.materialGlId());
        }
        march.begin();
        march.setSampler("ColorSampler", 0);
        march.setSampler("DepthSampler", 1);
        march.setInt("HasGBuffer", hasGBuffer ? 1 : 0);
        if (hasGBuffer) {
            march.setSampler("GNormalSampler", 2);
            march.setSampler("GMaterialSampler", 3);
        }
        march.setMatrix4("ViewProj", cam.viewProj);
        march.setMatrix4("InvViewProj", cam.invViewProj);
        march.setMatrix4("View", cam.view);
        march.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        march.setFloat("Intensity", s.intensity());
        march.setInt("MaxSteps", s.maxSteps());
        march.setFloat("Stride", s.stride());
        march.setFloat("MaxDistance", s.maxDistance());
        march.setFloat("Thickness", s.thickness());
        march.setFloat("EdgeFade", s.edgeFade());
        march.setFloat("Reflectivity", s.reflectivity());
        march.setFloat("Frame", (float) (frameCounter++ & 63));
        march.draw();
        reflection.end();

        Framebuffer resolved = reflection;
        if (s.temporal()) {
            Framebuffer hPrev = usingA ? historyA : historyB;
            Framebuffer hCurr = usingA ? historyB : historyA;
            hCurr.begin();
            GlState.bindTexture(0, reflection.colorTextureGlId(0));
            GlState.bindTexture(1, (historyValid && hPrev.isAllocated() ? hPrev : reflection).colorTextureGlId(0));
            GlState.bindTexture(2, capture.depthTextureGlId());
            temporal.begin();
            temporal.setSampler("Raw", 0);
            temporal.setSampler("History", 1);
            temporal.setSampler("DepthSampler", 2);
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

        int prevFbo = MainTargetFramebuffer.bind();
        try {
            GlState.bindTexture(0, resolved.colorTextureGlId(0));
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
            composite.begin();
            composite.setSampler("ReflectionSampler", 0);
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
        if (capture != null) { capture.dispose(); capture = null; }
        if (reflection != null) { reflection.dispose(); reflection = null; }
        if (historyA != null) { historyA.dispose(); historyA = null; }
        if (historyB != null) { historyB.dispose(); historyB = null; }
        reflectionScale = -1f;
        historyValid = false;
    }

    private void ensureReflection(float scale) {
        if (reflection == null || reflectionScale != scale) {
            if (reflection != null) reflection.dispose();
            if (historyA != null) historyA.dispose();
            if (historyB != null) historyB.dispose();
            reflection = Framebuffers.screen("SSR Reflection", scale, FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
            historyA = Framebuffers.screen("SSR History A", scale, FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
            historyB = Framebuffers.screen("SSR History B", scale, FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
            reflectionScale = scale;
            historyValid = false;
        }
    }
}
