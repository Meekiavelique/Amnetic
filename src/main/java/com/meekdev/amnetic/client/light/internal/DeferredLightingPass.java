package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.compat.IrisCompat;
import java.util.List;
import java.util.ArrayList;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.material.internal.MaterialParams;
import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.light.LightSettings;
import com.meekdev.amnetic.client.light.LightType;
import com.meekdev.amnetic.client.material.internal.ShadingModelRegistry;
import com.meekdev.amnetic.client.render.*;
import com.meekdev.amnetic.client.shadow.ShadowSettings;
import com.meekdev.amnetic.client.shadow.internal.PointShadowArray;
import com.meekdev.amnetic.client.shadow.internal.ShadowMapPass;
import com.meekdev.amnetic.client.shadow.internal.SpotShadowAtlas;
import com.meekdev.amnetic.client.shadow.internal.SunShadowCascades;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import org.joml.FrustumIntersection;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL40;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

public final class DeferredLightingPass extends ScreenPass {

    public static final DeferredLightingPass INSTANCE = new DeferredLightingPass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/light/deferred.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/light/deferred.fsh");

    public static int debugMode = 0;

    private Framebuffer capture;
    private final List<Light> packed = new ArrayList<>();
    private LightBuffer lightBuffer;
    private Framebuffer surfaceTarget;
    private ShaderProgram surfaceUpscale;
    private float surfaceScale = -1f;
    private final FrustumIntersection frustum = new FrustumIntersection();

    private DeferredLightingPass() { super("Light"); }

    private boolean volumetricOnly;
    private Framebuffer volumetricTarget;
    private int frameCounter;

    public void renderSurface() {
        volumetricOnly = false; volumetricTarget = null; dispatch();
    }

    public void renderVolumetricRaw(Framebuffer target) {
        if (!LightSettings.defaults().volumetric()) return;
        volumetricOnly = true;
        volumetricTarget = target;
        frameCounter++;
        dispatch();
        volumetricTarget = null;
    }

    public void render() { renderSurface(); }

    @Override protected boolean skipUnderIris() { return false; }

    @Override protected boolean enabled() {
        return !LightRegistry.INSTANCE.isEmpty()
                && (!IrisCompat.shadersActive() || (IrisCompat.compositing() && !volumetricOnly));
    }

    @Override
    protected ShaderProgram createProgram() {
        capture = Framebuffers.screen("Deferred Lighting Capture", FramebufferSpec.builder()
                .color(ColorFormat.RGBA8)
                .depthTexture()
                .build());
        lightBuffer = new LightBuffer(LightSettings.defaults().maxLights());
        return new ShaderProgram(VSH, FSH);
    }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram program) {
        if (ShadingModelRegistry.INSTANCE.consumeDirty()) program.invalidate();

        frustum.set(cam.viewProj);
        boolean shaderPack = IrisCompat.compositing();
        boolean volumes = LightSettings.defaults().lightVolumes() && !volumetricOnly && debugMode == 0
                && !shaderPack;
        packed.clear();
        int count = lightBuffer.pack(LightRegistry.INSTANCE.all(), cam.eye, frustum,
                volumes ? packed : null);
        if (count == 0) return false;

        boolean anyDirectional = false;
        if (volumes) {
            for (Light l : packed) {
                if (l.type() == LightType.DIRECTIONAL) { anyDirectional = true; break; }
            }
        }
        // the fullscreen pass recomposes the whole frame, which is only needed when something
        // darkens it (sun/directional) or replaces it (custom shading, debug, volumetrics)
        boolean needsFullscreen = !volumes
                || volumetricOnly
                || debugMode != 0
                || anyDirectional
                || (!shaderPack && ShadowMapPass.INSTANCE.sunActive())
                || (!shaderPack && ShadingModelRegistry.INSTANCE.hasCustomModels());
        // Upscaling costs more than it saves for just a few light volumes.
        boolean reducedSurface = volumes && !needsFullscreen && count >= 8
                && LightSettings.defaults().surfaceScale() < 1f;
        if (reducedSurface) {
            ensureSurfaceTarget(LightSettings.defaults().surfaceScale());
            surfaceTarget.begin();
            try { surfaceTarget.clear(0f, 0f, 0f, 0f); }
            finally { surfaceTarget.end(); }
            surfaceTarget.blitDepthFromMain();
        }

        if (!volumetricOnly) capture.blitColorFromMain();
        capture.blitDepthFromMain();

        boolean toHalf = volumetricOnly && volumetricTarget != null;
        Framebuffer destination = toHalf ? volumetricTarget : reducedSurface ? surfaceTarget : null;
        int prevFbo = -1;
        if (destination != null) destination.begin(); else prevFbo = MainTargetFramebuffer.bind();
        try {
            GlState.bindTexture(0, capture.colorTextureGlId(0));
            GlState.bindTexture(1, capture.depthTextureGlId());
            boolean hasGBuffer = !shaderPack && GBufferTargets.INSTANCE.isPopulated();
            if (hasGBuffer) {
                GlState.bindTexture(2, GBufferTargets.INSTANCE.normalGlId());
                GlState.bindTexture(3, GBufferTargets.INSTANCE.materialGlId());
                GlState.bindTexture(10, GBufferTargets.INSTANCE.albedoGlId());
            }
            boolean shadows = !shaderPack && ShadowMapPass.INSTANCE.isActive();
            boolean sunShadows = !shaderPack && ShadowMapPass.INSTANCE.sunActive();
            if (shadows) {
                GlState.bindTexture(4, SpotShadowAtlas.glTextureId());
                GlState.bindTexture(6, SpotShadowAtlas.colorTextureId());
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 5);
                GL11.glBindTexture(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, PointShadowArray.glTextureId());
                GL33.glBindSampler(5, 0);
                GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            }
            if (sunShadows) {
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 8);
                GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, SunShadowCascades.glTextureId());
                GL33.glBindSampler(8, 0);
                GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            }
            if (!shaderPack) GlState.bindTexture(9, levelLightmapGlId());
            lightBuffer.bind();
            if (!shaderPack) MaterialParams.INSTANCE.bind();

            program.begin();
            applyShared(program, cam, shadows, sunShadows, hasGBuffer, count, volumes);

            if (toHalf) {
                GlStateManager._disableBlend();
                GL11.glDisable(GL11.GL_BLEND);
            } else if (debugMode == 0) {
                // replace, not additive: the shader outputs the full recomposed scene (captured scene darkened
                // by sun-shadow occlusion + local lights + specular). additive blending can't darken, which a
                // daylight directional sun needs. go check the type==2 block in deferred.fsh
                GlStateManager._disableBlend();
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (needsFullscreen) program.draw();

            if (volumes) {
                GlState.bindTexture(0, capture.colorTextureGlId(0));
                GlState.bindTexture(1, capture.depthTextureGlId());
                LightVolumePass.INSTANCE.render(cam, packed,
                        reducedSurface ? surfaceTarget.width() : capture.width(),
                        reducedSurface ? surfaceTarget.height() : capture.height(),
                        p -> applyShared(p, cam, shadows, sunShadows, hasGBuffer, count, volumes));
            }
        } finally {
            GlState.bindTexture(4, 0);
            GlState.bindTexture(6, 0);
            GlState.bindTexture(7, 0);
            GlState.bindTexture(9, 0);
            GlState.bindTexture(10, 0);
            TexelBuffer.unbind(LightBuffer.UNIT);
            TexelBuffer.unbind(MaterialParams.UNIT);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 5);
            GL11.glBindTexture(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, 0);
            GL33.glBindSampler(5, 0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 8);
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, 0);
            GL33.glBindSampler(8, 0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            if (destination != null) destination.end(); else MainTargetFramebuffer.restore(prevFbo);
        }
        if (reducedSurface) compositeSurface(cam);
        return true;
    }

    private void ensureSurfaceTarget(float scale) {
        if (surfaceTarget != null && surfaceScale == scale) return;
        if (surfaceTarget != null) surfaceTarget.dispose();
        surfaceTarget = Framebuffers.screen("Local light accumulation", scale,
                FramebufferSpec.builder().color(ColorFormat.RGBA16F).depthTexture().build());
        surfaceScale = scale;
        if (surfaceUpscale == null) surfaceUpscale = new ShaderProgram(VSH,
                Identifier.fromNamespaceAndPath("amnetic", "shaders/light/upscale.fsh"));
    }

    private void compositeSurface(CameraSnapshot cam) {
        int previous = MainTargetFramebuffer.bind();
        try {
            GlStateManager._disableDepthTest(); GL11.glDisable(GL11.GL_DEPTH_TEST);
            GlStateManager._depthMask(false); GL11.glDepthMask(false);
            GlStateManager._disableCull(); GL11.glDisable(GL11.GL_CULL_FACE);
            GlStateManager._enableBlend(); GL11.glEnable(GL11.GL_BLEND);
            GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            GlState.bindTexture(0, surfaceTarget.colorTextureGlId(0));
            GlState.bindTexture(1, capture.depthTextureGlId());
            GlState.bindTexture(2, surfaceTarget.depthTextureGlId());
            surfaceUpscale.begin();
            surfaceUpscale.setSampler("LightSampler", 0);
            surfaceUpscale.setSampler("DepthSampler", 1);
            surfaceUpscale.setSampler("LowDepthSampler", 2);
            surfaceUpscale.setMatrix4("InvViewProj", cam.invViewProj);
            surfaceUpscale.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
            surfaceUpscale.draw();
        } finally { MainTargetFramebuffer.restore(previous); }
    }

    @Override
    protected void onDispose() {
        if (lightBuffer != null) { lightBuffer.close(); lightBuffer = null; }
        if (capture != null) { capture.dispose(); capture = null; }
        if (surfaceTarget != null) { surfaceTarget.dispose(); surfaceTarget = null; }
        if (surfaceUpscale != null) { surfaceUpscale.close(); surfaceUpscale = null; }
        surfaceScale = -1f;
    }

    private final Set<Identifier> loadedCookies = new HashSet<>();

    private static int levelLightmapGlId() {
        return VanillaCompat.levelLightmapGlId();
    }

    private int cookieGlId(Identifier id) {
        if (id == null) return 0;
        var tm = Minecraft.getInstance().getTextureManager();
        if (!ImportedTextures.isImported(id) && loadedCookies.add(id)) {
            try { VanillaCompat.loadTexture(tm, id); }
            catch (Throwable t) { return 0; }
        }
        return VanillaCompat.glId(tm.getTexture(id));
    }

    private void applyShared(ShaderProgram p, CameraSnapshot cam, boolean shadows, boolean sunShadows,
                             boolean hasGBuffer, int count, boolean volumes) {
        p.setSampler("AlbedoSampler", 0);
        p.setSampler("DepthSampler", 1);
        p.setSampler("GNormalSampler", 2);
        p.setSampler("GMaterialSampler", 3);
        p.setSampler("SpotShadowAtlas", 4);
        p.setSampler("PointShadowArray", 5);
        p.setSampler("SpotShadowColor", 6);
        p.setSampler("LightmapSampler", 9);
        p.setSampler("GAlbedoSampler", 10);
        p.setSampler("LightTexels", LightBuffer.UNIT);
        p.setSampler("MaterialTexels", MaterialParams.UNIT);
        p.setInt("ShadowActive", shadows ? 1 : 0);
        if (shadows) {
            ShadowSettings ss = ShadowSettings.defaults();
            p.setMatrix4Array("SpotViewProj", ShadowMapPass.INSTANCE.spotViewProj(), ShadowSettings.MAX_SPOT);
            p.setFloat("ShadowAtlasSize", SpotShadowAtlas.atlasSize());
            p.setFloat("ShadowTileSize", SpotShadowAtlas.tileSize());
            p.setInt("ShadowGridX", SpotShadowAtlas.GRID);
            p.setFloat("ShadowBias", ss.bias());
            p.setFloat("ShadowNormalBias", ss.normalBias());
            p.setFloat("ShadowSoftnessTexels", ss.softness());
            p.setFloat("ShadowFadeStart", ss.fadeStartDistance());
            p.setFloat("ShadowFadeEnd", ss.maxDistance());
            p.setInt("ShadowPcss", ss.pcss() ? 1 : 0);
            p.setFloat("ShadowLightSize", ss.lightSize());
        }
        p.setSampler("SunShadowMap", 8);
        p.setInt("SunShadowActive", sunShadows ? 1 : 0);
        if (sunShadows) {
            ShadowMapPass pass = ShadowMapPass.INSTANCE;
            p.setMatrix4Array("SunViewProj", pass.sunViewProj(), ShadowSettings.MAX_CASCADES);
            float[] radii = pass.sunCascadeRadius();
            for (int c = 0; c < ShadowSettings.MAX_CASCADES; c++) {
                p.setFloat("SunCascadeRadius[" + c + "]", radii[c]);
            }
            p.setInt("SunCascadeCount", pass.sunCascadeCount());
            p.setFloat("SunShadowRes", SunShadowCascades.resolution());
            p.setFloat("SunShadowDistance", ShadowMapPass.INSTANCE.sunDistance());
        }
        p.setInt("HasGBuffer", hasGBuffer ? 1 : 0);
        p.setInt("ShaderPackComposite", IrisCompat.compositing() ? 1 : 0);
        p.setInt("DepthReversed", IrisCompat.compositing() && IrisCompat.reverseDepth() ? 1 : 0);
        p.setMatrix4("InvViewProj", cam.invViewProj);
        p.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        p.setInt("LightCount", count);
        p.setFloat("LightTime", (float) ((System.nanoTime() / 1.0e9) % 3600.0));
        p.setInt("SkipLocalLights", volumes ? 1 : 0);
        LightSettings ls = LightSettings.defaults();
        p.setInt("VolumetricSteps", ls.volumetricSteps());
        p.setFloat("VolumetricStrength", ls.volumetricStrength());
        p.setFloat("VolumetricDensity", ls.volumetricDensity());
        p.setFloat("VolumetricAniso", ls.volumetricAniso());
        p.setInt("VolumetricShadows", (shadows && ls.volumetricShadows()) ? 1 : 0);
        p.setMatrix4("ViewProj", cam.viewProj);
        p.setInt("ContactSteps", ls.contactSteps());
        p.setFloat("ContactDistance", ls.contactDistance());
        p.setFloat("ContactThickness", ls.contactThickness());
        int cookieGl = cookieGlId(ls.cookieTexture());
        if (cookieGl != 0) GlState.bindTexture(7, cookieGl);
        p.setSampler("CookieSampler", 7);
        p.setInt("HasCookie", cookieGl != 0 ? 1 : 0);
        p.setInt("DebugMode", debugMode);
        p.setInt("VolumetricOnly", volumetricOnly ? 1 : 0);
        p.setFloat("TemporalOffset", volumetricOnly ? (frameCounter & 7) / 8f : 0f);
    }

}
