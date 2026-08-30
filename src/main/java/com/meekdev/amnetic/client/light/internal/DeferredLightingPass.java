package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.material.internal.MaterialParams;
import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.light.LightSettings;
import com.meekdev.amnetic.client.material.internal.ShadingModelRegistry;
import com.meekdev.amnetic.client.render.*;
import com.meekdev.amnetic.client.shadow.ShadowSettings;
import com.meekdev.amnetic.client.shadow.internal.PointShadowArray;
import com.meekdev.amnetic.client.shadow.internal.ShadowMapPass;
import com.meekdev.amnetic.client.shadow.internal.SpotShadowAtlas;
import com.meekdev.amnetic.client.shadow.internal.SunShadowCascades;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
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
        int count = lightBuffer.pack(LightRegistry.INSTANCE.all(), cam.eye, frustum);
        if (count == 0) return false;

        if (!volumetricOnly) capture.blitColorFromMain();
        capture.blitDepthFromMain();

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
            program.setSampler("AlbedoSampler", 0);
            program.setSampler("DepthSampler", 1);
            program.setSampler("GNormalSampler", 2);
            program.setSampler("GMaterialSampler", 3);
            program.setSampler("SpotShadowAtlas", 4);
            program.setSampler("PointShadowArray", 5);
            program.setSampler("SpotShadowColor", 6);
            program.setSampler("LightmapSampler", 9);
            program.setInt("ShadowActive", shadows ? 1 : 0);
            if (shadows) {
                ShadowSettings ss = ShadowSettings.defaults();
                program.setMatrix4Array("SpotViewProj", ShadowMapPass.INSTANCE.spotViewProj(), ShadowSettings.MAX_SPOT);
                program.setFloat("ShadowAtlasSize", SpotShadowAtlas.atlasSize());
                program.setFloat("ShadowTileSize", SpotShadowAtlas.tileSize());
                program.setInt("ShadowGridX", SpotShadowAtlas.GRID);
                program.setFloat("ShadowBias", ss.bias());
                program.setFloat("ShadowNormalBias", ss.normalBias());
                program.setFloat("ShadowSoftnessTexels", ss.softness());
                program.setFloat("ShadowFadeStart", ss.fadeStartDistance());
                program.setFloat("ShadowFadeEnd", ss.maxDistance());
                program.setInt("ShadowPcss", ss.pcss() ? 1 : 0);
                program.setFloat("ShadowLightSize", ss.lightSize());
            }
            program.setSampler("SunShadowMap", 8);
            program.setInt("SunShadowActive", sunShadows ? 1 : 0);
            if (sunShadows) {
                ShadowMapPass pass = ShadowMapPass.INSTANCE;
                program.setMatrix4Array("SunViewProj", pass.sunViewProj(), ShadowSettings.MAX_CASCADES);
                float[] radii = pass.sunCascadeRadius();
                for (int c = 0; c < ShadowSettings.MAX_CASCADES; c++) {
                    program.setFloat("SunCascadeRadius[" + c + "]", radii[c]);
                }
                program.setInt("SunCascadeCount", pass.sunCascadeCount());
                program.setFloat("SunShadowRes", SunShadowCascades.resolution());
                program.setFloat("SunShadowDistance", ShadowSettings.defaults().sunDistance());
            }
            program.setInt("HasGBuffer", hasGBuffer ? 1 : 0);
            program.setMatrix4("InvViewProj", cam.invViewProj);
            program.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
            program.setInt("LightCount", count);
            LightSettings ls = LightSettings.defaults();
            program.setInt("VolumetricSteps", ls.volumetricSteps());
            program.setFloat("VolumetricStrength", ls.volumetricStrength());
            program.setFloat("VolumetricDensity", ls.volumetricDensity());
            program.setFloat("VolumetricAniso", ls.volumetricAniso());
            program.setInt("VolumetricShadows", (shadows && ls.volumetricShadows()) ? 1 : 0);
            program.setMatrix4("ViewProj", cam.viewProj);
            program.setInt("ContactSteps", ls.contactSteps());
            program.setFloat("ContactDistance", ls.contactDistance());
            program.setFloat("ContactThickness", ls.contactThickness());
            int cookieGl = cookieGlId(ls.cookieTexture());
            if (cookieGl != 0) GlState.bindTexture(7, cookieGl);
            program.setSampler("CookieSampler", 7);
            program.setInt("HasCookie", cookieGl != 0 ? 1 : 0);
            program.setInt("DebugMode", debugMode);
            program.setInt("VolumetricOnly", volumetricOnly ? 1 : 0);
            program.setFloat("TemporalOffset", volumetricOnly ? (frameCounter & 7) / 8f : 0f);

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
            program.draw();
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
        var renderer = Minecraft.getInstance().gameRenderer;
        if (renderer == null) return 0;
        var view = renderer.levelLightmap();
        return (view != null && view.texture() instanceof GlTexture gl) ? gl.glId() : 0;
    }

    private int cookieGlId(Identifier id) {
        if (id == null) return 0;
        var tm = Minecraft.getInstance().getTextureManager();
        if (!ImportedTextures.isImported(id) && loadedCookies.add(id)) {
            try { tm.registerAndLoad(id, new SimpleTexture(id)); }
            catch (Throwable t) { return 0; }
        }
        var tex = tm.getTexture(id);
        return (tex != null && tex.getTexture() instanceof GlTexture gl) ? gl.glId() : 0;
    }
}
