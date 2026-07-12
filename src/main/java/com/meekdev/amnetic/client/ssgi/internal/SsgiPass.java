package com.meekdev.amnetic.client.ssgi.internal;

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
import com.meekdev.amnetic.client.ssgi.SsgiSettings;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class SsgiPass extends ScreenPass {

    public static final SsgiPass INSTANCE = new SsgiPass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssgi/ssgi.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssgi/ssgi.fsh");
    private static final Identifier COMPOSITE_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssgi/composite.fsh");
    private static final Identifier TEMPORAL_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssgi/temporal.fsh");

    private ShaderProgram composite;
    private ShaderProgram temporal;
    private Framebuffer capture; // scene color + depth
    private Framebuffer gi; // scaled raw single-sample indirect result
    // ping-ponged temporal history: rgb = accumulated GI, a = camera-relative distance at that sample
    // (used for the disocclusion check next frame)
    private Framebuffer historyA;
    private Framebuffer historyB;
    private boolean historyPingIsA = true;
    private boolean hasHistory;
    private Matrix4f prevViewProj;
    private Vec3 prevEye;
    private float giScale = -1f;
    private SsgiSettings settings;

    private SsgiPass() { super("SSGI"); }

    public void render(SsgiSettings s) {
        this.settings = s;
        dispatch();
    }

    @Override protected boolean enabled() { return settings != null && settings.isEnabled(); }

    @Override
    protected ShaderProgram createProgram() {
        composite = new ShaderProgram(VSH, COMPOSITE_FSH);
        temporal = new ShaderProgram(VSH, TEMPORAL_FSH);
        return new ShaderProgram(VSH, FSH);
    }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram ssgi) {
        SsgiSettings s = settings;
        ensureGi(s.scale());

        capture.blitColorFromMain();
        capture.blitDepthFromMain();

        gi.begin();
        boolean hasGBuffer = GBufferTargets.INSTANCE.isPopulated();
        GlState.bindTexture(0, capture.colorTextureGlId(0));
        GlState.bindTexture(1, capture.depthTextureGlId());
        GlState.bindTexture(2, hasGBuffer ? GBufferTargets.INSTANCE.normalGlId() : capture.depthTextureGlId());
        ssgi.begin();
        ssgi.setSampler("ColorSampler", 0);
        ssgi.setSampler("DepthSampler", 1);
        ssgi.setSampler("GNormalSampler", 2);
        ssgi.setInt("HasGBuffer", hasGBuffer ? 1 : 0);
        ssgi.setMatrix4("ViewProj", cam.viewProj);
        ssgi.setMatrix4("InvViewProj", cam.invViewProj);
        ssgi.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        ssgi.setFloat("Radius", s.radius());
        ssgi.setFloat("Intensity", s.intensity());
        ssgi.draw();
        gi.end();

        Framebuffer historyRead = historyPingIsA ? historyA : historyB;
        Framebuffer historyWrite = historyPingIsA ? historyB : historyA;

        float x = (float) (cam.eye.x - (prevEye != null ? prevEye.x : cam.eye.x));
        float y = (float) (cam.eye.y - (prevEye != null ? prevEye.y : cam.eye.y));
        float z = (float) (cam.eye.z - (prevEye != null ? prevEye.z : cam.eye.z));

        historyWrite.begin();
        GlState.bindTexture(0, gi.colorTextureGlId(0));
        GlState.bindTexture(1, capture.depthTextureGlId());
        GlState.bindTexture(2, historyRead.colorTextureGlId(0));
        temporal.begin();
        temporal.setSampler("CurrentSampler", 0);
        temporal.setSampler("DepthSampler", 1);
        temporal.setSampler("HistorySampler", 2);
        temporal.setMatrix4("InvViewProj", cam.invViewProj);
        temporal.setMatrix4("PrevViewProj", prevViewProj != null ? prevViewProj : cam.viewProj);
        temporal.setVec3("EyeDelta", x, y, z);
        temporal.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        temporal.setInt("HasHistory", hasHistory ? 1 : 0);
        temporal.setFloat("HistoryBlend", s.historyBlend());
        temporal.draw();
        historyWrite.end();

        historyPingIsA = !historyPingIsA;
        hasHistory = true;
        prevViewProj = new Matrix4f(cam.viewProj);
        prevEye = cam.eye;

        int prevFbo = MainTargetFramebuffer.bind();
        try {
            GlState.bindTexture(0, historyWrite.colorTextureGlId(0));
            GlState.bindTexture(1, capture.depthTextureGlId());
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            composite.begin();
            composite.setSampler("GiSampler", 0);
            composite.setSampler("DepthSampler", 1);
            composite.setMatrix4("InvViewProj", cam.invViewProj);
            composite.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
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
        if (gi != null) { gi.dispose(); gi = null; }
        if (historyA != null) { historyA.dispose(); historyA = null; }
        if (historyB != null) { historyB.dispose(); historyB = null; }
        giScale = -1f;
        hasHistory = false;
        prevViewProj = null;
        prevEye = null;
    }

    private void ensureGi(float scale) {
        if (gi == null || giScale != scale) {
            if (capture != null) capture.dispose();
            if (gi != null) gi.dispose();
            if (historyA != null) historyA.dispose();
            if (historyB != null) historyB.dispose();
            // capture scene color/depth at the same reduced scale the GI trace and temporal passes
            // read at, avoids blitting a full-res copy neither of them uses
            capture = Framebuffers.screen("SSGI Capture", scale,
                    FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build());
            gi = Framebuffers.screen("SSGI GI", scale, FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
            FramebufferSpec historySpec = FramebufferSpec.builder().color(ColorFormat.RGBA16F).build();
            historyA = Framebuffers.screen("SSGI History A", scale, historySpec);
            historyB = Framebuffers.screen("SSGI History B", scale, historySpec);
            // force allocation up front: the read side of the ping-pong is sampled via
            // colorTextureGlId() before ever having begin() called on it otherwise
            historyA.begin();
            historyA.clear(0f, 0f, 0f, 0f);
            historyA.end();
            historyB.begin();
            historyB.clear(0f, 0f, 0f, 0f);
            historyB.end();
            giScale = scale;
            hasHistory = false;
        }
    }
}
