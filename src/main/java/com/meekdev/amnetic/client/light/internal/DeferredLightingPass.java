package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
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

    @Override protected boolean enabled() { return !LightRegistry.INSTANCE.isEmpty(); }

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
        boolean volumes = LightSettings.defaults().lightVolumes() && !volumetricOnly && debugMode == 0;
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
        boolean gbufferDepth = GBufferTargets.INSTANCE.isPopulated()
                && GBufferTargets.INSTANCE.depthGlId() != 0;
        // the fullscreen pass recomposes the whole frame, which is only needed when something
        // darkens it (sun/directional) or replaces it (custom shading, debug, volumetrics)
        boolean needsFullscreen = !volumes
                || volumetricOnly
                || debugMode != 0
                || anyDirectional
                || ShadowMapPass.INSTANCE.sunActive()
                || ShadingModelRegistry.INSTANCE.hasCustomModels();

        if (!volumetricOnly) capture.blitColorFromMain();
        if (needsFullscreen || !gbufferDepth || volumes) capture.blitDepthFromMain();

        boolean toHalf = volumetricOnly && volumetricTarget != null;
        int prevFbo = -1;
        if (toHalf) volumetricTarget.begin(); else prevFbo = MainTargetFramebuffer.bind();
        try {
            GlState.bindTexture(0, capture.colorTextureGlId(0));
            GlState.bindTexture(1, capture.depthTextureGlId());
            boolean hasGBuffer = GBufferTargets.INSTANCE.isPopulated();
            if (hasGBuffer) {
                GlState.bindTexture(2, GBufferTargets.INSTANCE.normalGlId());
                GlState.bindTexture(3, GBufferTargets.INSTANCE.materialGlId());
            }
            boolean shadows = ShadowMapPass.INSTANCE.isActive();
            boolean sunShadows = ShadowMapPass.INSTANCE.sunActive();
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
            GlState.bindTexture(9, levelLightmapGlId());
            lightBuffer.bind(0);
            MaterialParams.INSTANCE.bind(1);

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
                        capture.width(), capture.height(),
                        p -> applyShared(p, cam, shadows, sunShadows, hasGBuffer, count, volumes));
            }
        } finally {
            GlState.bindTexture(4, 0);
            GlState.bindTexture(6, 0);
            GlState.bindTexture(7, 0);
            GlState.bindTexture(9, 0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 5);
            GL11.glBindTexture(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, 0);
            GL33.glBindSampler(5, 0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 8);
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, 0);
            GL33.glBindSampler(8, 0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            if (toHalf) volumetricTarget.end(); else MainTargetFramebuffer.restore(prevFbo);
        }
        return true;
    }

    @Override
    protected void onDispose() {
        if (lightBuffer != null) { lightBuffer.close(); lightBuffer = null; }
        if (capture != null) { capture.dispose(); capture = null; }
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
            p.setFloat("SunShadowDistance", ShadowSettings.defaults().sunDistance());
        }
        p.setInt("HasGBuffer", hasGBuffer ? 1 : 0);
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
