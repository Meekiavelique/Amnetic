package com.meekdev.amnetic.client.ssao.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.gbuffer.GBuffer;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.ssao.SsaoSettings;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

public final class SsaoPass extends ScreenPass {

    public static final SsaoPass INSTANCE = new SsaoPass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/ssao.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/ssao.fsh");
    private static final Identifier PREP_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/prep.fsh");
    private static final Identifier COMPOSITE_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/composite.fsh");
    private static final Identifier TEMPORAL_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/temporal.fsh");
    private static final Identifier BLUR_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/blur.fsh");

    private ShaderProgram prep;
    private ShaderProgram composite;
    private ShaderProgram temporal;
    private ShaderProgram blur;
    private Framebuffer linear;
    private Framebuffer raw;
    private Framebuffer across;
    private Framebuffer blurred;
    private Framebuffer historyA, historyB;
    private boolean usingA;
    private float aoScale = -1f;
    private int outputFbo;
    private SsaoSettings settings;

    private final Matrix4f prevViewProj = new Matrix4f();
    private final Matrix4f invView = new Matrix4f();
    private double prevEyeX, prevEyeY, prevEyeZ;
    private boolean historyValid;
    private int frame;

    //? if >=26.2 {
    private final NativeSsaoPass nativePass = new NativeSsaoPass();
    //?}

    private SsaoPass() { super("SSAO"); }

    public void render(SsaoSettings s) {
        //? if >=26.2 {
        if (s.isEnabled() && !com.meekdev.amnetic.client.compat.IrisCompat.shadersActive()) {
            CameraSnapshot camera = CameraSnapshot.current();
            if (camera != null) nativePass.render(camera, s);
        }
        return;
        //?} else {
        /*this.settings = s;
        dispatch();
        *///?}
    }

    @Override protected boolean enabled() { return settings != null && settings.isEnabled(); }

    @Override
    protected ShaderProgram createProgram() {
        prep = new ShaderProgram(VSH, PREP_FSH);
        composite = new ShaderProgram(VSH, COMPOSITE_FSH);
        temporal = new ShaderProgram(VSH, TEMPORAL_FSH);
        blur = new ShaderProgram(VSH, BLUR_FSH);
        return new ShaderProgram(VSH, FSH);
    }

    private static void view(ShaderProgram p, CameraSnapshot cam) {
        Matrix4f proj = cam.projection;
        p.setVec4("ProjInfo", 1f / proj.m00(), 1f / proj.m11(), proj.m20(), proj.m21());
        p.setVec2("DepthInfo", proj.m22(), proj.m32());
        p.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
    }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram gtao) {
        SsaoSettings s = settings;
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main == null) return false;
        int depthId = VanillaCompat.depthTextureGlId(main);
        int colorId = VanillaCompat.colorTextureGlId(main);
        if (depthId <= 0 || colorId <= 0) return false;
        ensureBuffers(s.scale());

        boolean gbuffer = GBuffer.isPopulated();
        int shade = gbuffer ? GBuffer.shadeTextureGlId() : 0;
        linear.begin();
        GlState.bindTexture(0, depthId);
        GlState.bindTexture(1, shade);
        prep.begin();
        prep.setSampler("DepthSampler", 0);
        prep.setSampler("ShadeSampler", 1);
        view(prep, cam);
        prep.draw();
        linear.end();

        raw.begin();
        GlState.bindTexture(0, linear.colorTextureGlId(0));
        GlState.bindTexture(1, linear.colorTextureGlId(1));
        gtao.begin();
        gtao.setSampler("LinearSampler", 0);
        gtao.setSampler("NormalSampler", 1);
        view(gtao, cam);
        gtao.setFloat("Radius", s.radius());
        gtao.setFloat("Intensity", s.intensity());
        gtao.setFloat("Bias", s.bias());
        gtao.setFloat("Power", s.power());
        gtao.setFloat("PixelScale", 0.5f * raw.height() * cam.projection.m11());
        gtao.setInt("Frame", s.temporal() ? frame : 0);
        gtao.draw();
        raw.end();

        Framebuffer hPrev = usingA ? historyA : historyB;
        Framebuffer hCurr = usingA ? historyB : historyA;
        boolean useHistory = s.temporal() && historyValid && hPrev.isAllocated();
        hCurr.begin();
        GlState.bindTexture(0, raw.colorTextureGlId(0));
        GlState.bindTexture(1, linear.colorTextureGlId(0));
        GlState.bindTexture(2, (useHistory ? hPrev : raw).colorTextureGlId(0));
        GlState.bindTexture(3, gbuffer ? GBuffer.velocityTextureGlId() : 0);
        temporal.begin();
        temporal.setSampler("Velocity", 3);
        temporal.setInt("HasVelocity", gbuffer ? 1 : 0);
        temporal.setSampler("AoRaw", 0);
        temporal.setSampler("LinearSampler", 1);
        temporal.setSampler("History", 2);
        view(temporal, cam);
        temporal.setMatrix4("InvView", invView.set(cam.view).invert());
        temporal.setMatrix4("PrevViewProj", prevViewProj);
        temporal.setVec3("EyeDelta",
                (float) (cam.eye.x - prevEyeX), (float) (cam.eye.y - prevEyeY), (float) (cam.eye.z - prevEyeZ));
        temporal.setFloat("Feedback", useHistory ? s.feedback() : 0f);
        temporal.draw();
        hCurr.end();
        usingA = !usingA;
        historyValid = s.temporal();

        blur(cam, hCurr, across, 1, 0);
        blur(cam, across, blurred, 0, 1);

        prevViewProj.set(cam.viewProj);
        prevEyeX = cam.eye.x; prevEyeY = cam.eye.y; prevEyeZ = cam.eye.z;
        frame = (frame + 1) & 1023;

        int prevFbo = MainTargetFramebuffer.bind();
        try {
            if (outputFbo == 0) outputFbo = GL30.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, outputFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorId, 0);
            GlState.bindTexture(0, blurred.colorTextureGlId(0));
            GlState.bindTexture(1, linear.colorTextureGlId(0));
            GlState.bindTexture(2, depthId);
            GlState.bindTexture(3, shade);
            GlStateManager._enableBlend(0);
            GlStateManager._blendFuncSeparate(GL11.GL_ZERO, GL11.GL_SRC_COLOR, GL11.GL_ZERO, GL11.GL_ONE);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_ZERO, GL11.GL_SRC_COLOR, GL11.GL_ZERO, GL11.GL_ONE);
            composite.begin();
            composite.setSampler("AoSampler", 0);
            composite.setSampler("LinearSampler", 1);
            composite.setSampler("DepthSampler", 2);
            composite.setSampler("ShadeSampler", 3);
            view(composite, cam);
            composite.draw();
        } finally {
            MainTargetFramebuffer.restore(prevFbo);
        }
        return true;
    }

    private void blur(CameraSnapshot cam, Framebuffer from, Framebuffer to, int x, int y) {
        to.begin();
        GlState.bindTexture(0, from.colorTextureGlId(0));
        GlState.bindTexture(1, linear.colorTextureGlId(0));
        GlState.bindTexture(2, linear.colorTextureGlId(1));
        blur.begin();
        blur.setSampler("AoSampler", 0);
        blur.setSampler("LinearSampler", 1);
        blur.setSampler("NormalSampler", 2);
        blur.setVec2i("Direction", x, y);
        view(blur, cam);
        blur.draw();
        to.end();
    }

    @Override
    protected void onDispose() {
        //? if >=26.2 {
        nativePass.close();
        //?}
        if (prep != null) { prep.close(); prep = null; }
        if (composite != null) { composite.close(); composite = null; }
        if (temporal != null) { temporal.close(); temporal = null; }
        if (blur != null) { blur.close(); blur = null; }
        freeBuffers();
        if (outputFbo != 0) { GL30.glDeleteFramebuffers(outputFbo); outputFbo = 0; }
        aoScale = -1f;
        historyValid = false;
    }

    private void freeBuffers() {
        if (linear != null) { linear.dispose(); linear = null; }
        if (raw != null) { raw.dispose(); raw = null; }
        if (across != null) { across.dispose(); across = null; }
        if (blurred != null) { blurred.dispose(); blurred = null; }
        if (historyA != null) { historyA.dispose(); historyA = null; }
        if (historyB != null) { historyB.dispose(); historyB = null; }
    }

    private void ensureBuffers(float scale) {
        if (raw != null && aoScale == scale) return;
        freeBuffers();
        linear = Framebuffers.screen("SSAO Depth", scale,
                FramebufferSpec.builder().color(ColorFormat.R32F).color(ColorFormat.RGBA8).build());
        raw = Framebuffers.screen("SSAO Occlusion", scale, FramebufferSpec.builder().color(ColorFormat.R8).build());
        across = Framebuffers.screen("SSAO Blur Across", scale, FramebufferSpec.builder().color(ColorFormat.R8).build());
        blurred = Framebuffers.screen("SSAO Blur", scale, FramebufferSpec.builder().color(ColorFormat.R8).build());
        historyA = Framebuffers.screen("SSAO History A", scale, FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
        historyB = Framebuffers.screen("SSAO History B", scale, FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
        aoScale = scale;
        historyValid = false;
    }
}
