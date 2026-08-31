package com.meekdev.amnetic.client.bloom.internal;

import com.meekdev.amnetic.client.bloom.BloomSettings;
import com.meekdev.amnetic.client.emissive.EmissiveContext;
import com.meekdev.amnetic.client.emissive.EmissiveSources;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import net.minecraft.client.Minecraft;
import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

public final class BloomRenderer {

    private static final InstancePhase PHASE = InstancePhase.WORLD_LAST;
    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/bloom/fullscreen.vsh");
    private static final Identifier DOWN_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/bloom/downsample.fsh");
    private static final Identifier UP_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/bloom/upsample.fsh");
    private static final Identifier COMPOSITE_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/bloom/composite.fsh");
    private static final Identifier PREFILTER_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/bloom/prefilter.fsh");

    private final ShaderProgram downsample = new ShaderProgram(VSH, DOWN_FSH);
    private final ShaderProgram upsample = new ShaderProgram(VSH, UP_FSH);
    private final ShaderProgram composite = new ShaderProgram(VSH, COMPOSITE_FSH);
    private final ShaderProgram prefilter = new ShaderProgram(VSH, PREFILTER_FSH);

    private Framebuffer emissiveBuf;
    private Framebuffer sceneCapture;
    private Framebuffer[] mips;
    private float baseScale = -1f;
    private int levelCount = -1;
    private boolean occludeMode;

    private int brightQuery;
    private boolean hadBloom = true; // assume bloom until the first query result says otherwise
    private int staleQueryFrames;
    private static final int STALE_QUERY_LIMIT = 4;

    public void render(LevelRenderContext ctx, BloomSettings s) {
        if (!s.isEnabled()) return;
        boolean instEmissive = InstanceMeshRegistry.INSTANCE.hasEmissive(PHASE, s.isAll());
        boolean hookEmissive = !EmissiveSources.isEmpty();
        boolean gbufferEmissive = GBufferTargets.INSTANCE.isPopulated();
        boolean sceneBloom = s.threshold() > 0.0f;
        if (!instEmissive && !hookEmissive && !gbufferEmissive && !sceneBloom) return;

        ensureChain(s.scale(), s.levels(), s.isOcclude());

        if (brightQuery == 0) {
            brightQuery = GL15.glGenQueries();
        } else if (GL15.glGetQueryObjecti(brightQuery, GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_TRUE) {
            hadBloom = GL15.glGetQueryObjecti(brightQuery, GL15.GL_QUERY_RESULT) != 0;
            staleQueryFrames = 0;
        } else if (++staleQueryFrames > STALE_QUERY_LIMIT) {
            // the result never arrived; fail open rather than latch bloom off indefinitely
            hadBloom = true;
            staleQueryFrames = 0;
        }
        GL15.glBeginQuery(GL33.GL_ANY_SAMPLES_PASSED, brightQuery);
        try {
            renderSources(ctx, s, instEmissive, hookEmissive, gbufferEmissive, sceneBloom);
        } finally {
            GL15.glEndQuery(GL33.GL_ANY_SAMPLES_PASSED);
        }
        // the query only measures what passed the depth test last frame, so it cannot be
        // trusted to gate an explicitly registered source: looking away for one frame would
        // otherwise switch bloom off and keep it off
        boolean explicitSource = instEmissive || hookEmissive || gbufferEmissive;
        if (!hadBloom && !explicitSource) return;

        runPyramidAndComposite(s);
    }

    private void renderSources(LevelRenderContext ctx, BloomSettings s, boolean instEmissive,
                               boolean hookEmissive, boolean gbufferEmissive, boolean sceneBloom) {
        emissiveBuf.begin();
        emissiveBuf.clear(0f, 0f, 0f, 0f);
        emissiveBuf.end();
        if (instEmissive || hookEmissive) {
            if (s.isOcclude()) {
                emissiveBuf.blitDepthFromMain();
                GL11.glDepthFunc(GL11.GL_LEQUAL); // visible surfaces (equal depth) bloom
            }
            if (instEmissive) {
                InstanceMeshRegistry.INSTANCE.renderEmissive(PHASE, ctx, emissiveBuf, s.isAll());
            }
            if (hookEmissive) {
                emitRegisteredSources(s.isOcclude());
            }
        }

        if (sceneBloom || gbufferEmissive) {
            sceneCapture.blitColorFromMain();
            sceneCapture.blitDepthFromMain();
            emissiveBuf.begin();
            setupFullscreenState();
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            boolean hasGBuffer = GBufferTargets.INSTANCE.isPopulated();
            GlState.bindTexture(0, sceneCapture.colorTextureGlId(0));
            GlState.bindTexture(1, sceneCapture.depthTextureGlId());
            GlState.bindTexture(2, hasGBuffer ? GBufferTargets.INSTANCE.emissiveGlId() : sceneCapture.depthTextureGlId());
            prefilter.begin();
            prefilter.setSampler("Sampler", 0);
            prefilter.setSampler("DepthSampler", 1);
            prefilter.setSampler("EmissiveSampler", 2);
            prefilter.setInt("HasGBuffer", hasGBuffer ? 1 : 0);
            prefilter.setFloat("Threshold", s.threshold());
            prefilter.setFloat("Knee", s.knee());
            prefilter.draw();
            emissiveBuf.end();
            restoreState();
        }
    }

    // lets any renderer (entities, GeckoLib, a third-party mod) draw into the emissive buffer
    // without Amnetic depending on it
    private void emitRegisteredSources(boolean occlude) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        CameraSnapshot cam = CameraSnapshot.current();
        if (cam == null) return;
        emissiveBuf.begin();
        try {
            // sources draw world geometry, so establish known state instead of inheriting
            // whatever the previous pass left bound
            GlStateManager._enableBlend();
            GL11.glEnable(GL11.GL_BLEND);
            GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            GlStateManager._disableCull();
            GL11.glDisable(GL11.GL_CULL_FACE);
            GlStateManager._depthMask(false);
            GL11.glDepthMask(false);
            if (occlude) {
                GlStateManager._enableDepthTest();
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                // emissive block quads are coplanar with the world geometry already in this
                // depth buffer, so exact-equality LEQUAL fails at grazing angles and drops
                // every face except the one squarely facing the camera. bias them nearer.
                GlStateManager._polygonOffset(-1.0f, -1.0f);
                GlStateManager._enablePolygonOffset();
            } else {
                GlStateManager._disableDepthTest();
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }

            EmissiveSources.emitAll(new EmissiveContext(
                    mc.level, cam.eye, cam.view, cam.projection,
                    mc.getDeltaTracker().getGameTimeDeltaPartialTick(false),
                    emissiveBuf.width(), emissiveBuf.height()));
        } finally {
            GlStateManager._disablePolygonOffset();
            GlStateManager._depthMask(true);
            GL11.glDepthMask(true);
            emissiveBuf.end();
        }
    }

    private void runPyramidAndComposite(BloomSettings s) {
        downsampleInto(emissiveBuf, mips[0]);
        for (int i = 1; i < mips.length; i++) {
            downsampleInto(mips[i - 1], mips[i]);
        }

        for (int i = mips.length - 1; i > 0; i--) {
            Framebuffer from = mips[i];
            Framebuffer to = mips[i - 1];
            to.begin();
            setupFullscreenState();
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            bindTexture(from.colorTextureGlId(0));
            upsample.begin();
            upsample.setSampler("Sampler", 0);
            upsample.setVec2("TexelSize", 1f / from.width(), 1f / from.height());
            upsample.setFloat("Radius", 1.0f);
            upsample.draw();
            to.end();
        }

        Framebuffer result = mips[0];
        // normalize for the mip accumulation so more levels don't blow out the scene
        float intensity = s.intensity() * (2f / mips.length);
        int prevFbo = MainTargetFramebuffer.bind();
        try {
            setupFullscreenState();
            bindTexture(result.colorTextureGlId(0));
            composite.begin();
            composite.setSampler("Sampler", 0);
            composite.setFloat("Intensity", intensity);
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            composite.draw();
        } finally {
            MainTargetFramebuffer.restore(prevFbo);
            restoreState();
        }
    }

    private void downsampleInto(Framebuffer from, Framebuffer to) {
        to.begin();
        setupFullscreenState();
        bindTexture(from.colorTextureGlId(0));
        downsample.begin();
        downsample.setSampler("Sampler", 0);
        downsample.setVec2("TexelSize", 1f / from.width(), 1f / from.height());
        downsample.draw();
        to.end();
    }

    private void ensureChain(float scale, int levels, boolean occlude) {
        if (mips != null && baseScale == scale && levelCount == levels && occludeMode == occlude) return;
        if (mips != null) {
            for (Framebuffer fb : mips) fb.dispose();
        }
        if (emissiveBuf != null) emissiveBuf.dispose();
        if (sceneCapture != null) sceneCapture.dispose();

        // both buffers only feed the mip chain, which downsamples to `scale` immediately, so
        // capturing/prefiltering at full resolution would be wasted GPU work
        FramebufferSpec.Builder emissive = FramebufferSpec.builder().color(ColorFormat.RGBA16F);
        if (occlude) emissive.depthTexture();
        emissiveBuf = Framebuffers.screen("Bloom Emissive", scale, emissive.build());
        sceneCapture = Framebuffers.screen("Bloom Scene Capture", scale,
                FramebufferSpec.builder().color(ColorFormat.RGBA16F).depthTexture().build());

        FramebufferSpec spec = FramebufferSpec.builder().color(ColorFormat.RGBA16F).build();
        mips = new Framebuffer[levels];
        float sc = scale;
        for (int i = 0; i < levels; i++) {
            mips[i] = Framebuffers.screen("Bloom Mip " + i, sc, spec);
            sc *= 0.5f;
        }
        baseScale = scale;
        levelCount = levels;
        occludeMode = occlude;
    }

    private static void setupFullscreenState() { GlState.beginFullscreen(); }

    private static void bindTexture(int glId) { GlState.bindTexture(0, glId); }

    private static void restoreState() { GlState.endFullscreen(); }
}
