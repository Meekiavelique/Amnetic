package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.compat.IrisCompat;
import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.LightSettings;
import com.meekdev.amnetic.client.light.LightType;
import com.meekdev.amnetic.client.material.internal.MaterialParams;
import com.meekdev.amnetic.client.material.internal.ShadingModelRegistry;
import java.util.ArrayList;
import java.util.List;
import com.meekdev.amnetic.client.render.*;
import com.meekdev.amnetic.client.shadow.ShadowSettings;
import com.meekdev.amnetic.client.shadow.internal.PointShadowArray;
import com.meekdev.amnetic.client.shadow.internal.ShadowMapPass;
import com.meekdev.amnetic.client.shadow.internal.SpotShadowAtlas;
import com.meekdev.amnetic.client.shadow.internal.SunShadowCascades;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
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
//? if >=26.2 {
import com.mojang.renderpearl.api.GpuFormat;
//?}

public final class DeferredLightingPass extends ScreenPass {
    public static final DeferredLightingPass INSTANCE = new DeferredLightingPass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/light/deferred.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/light/deferred.fsh");
    private static final Identifier UPSCALE_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/light/upscale.fsh");

    private static final int ALBEDO_UNIT = 0;
    private static final int DEPTH_UNIT = 1;
    private static final int NORMAL_UNIT = 2;
    private static final int MATERIAL_UNIT = 3;
    private static final int SPOT_ATLAS_UNIT = 4;
    private static final int POINT_SHADOW_UNIT = 5;
    private static final int SPOT_COLOR_UNIT = 6;
    private static final int COOKIE_UNIT = 7;
    private static final int SUN_SHADOW_UNIT = 8;
    private static final int LIGHTMAP_UNIT = 9;
    private static final int GBUFFER_ALBEDO_UNIT = 10;
    private static final int SHADE_UNIT = 13;
    private static final int REDUCED_SURFACE_MIN_LIGHTS = 8;
    private static final double LIGHT_TIME_PERIOD = 3600.0;

    public static int debugMode = 0;

    private Framebuffer capture;
    private final List<Light> packed = new ArrayList<>();
    private LightBuffer lightBuffer;
    private Framebuffer surfaceTarget;
    private ShaderProgram surfaceUpscale;
    private float surfaceScale = -1f;
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Set<Identifier> loadedCookies = new HashSet<>();

    private boolean volumetricOnly;
    private Framebuffer volumetricTarget;
    private int frameCounter;
    //? if >=26.2 {
    private NativeDeferredLighting nativePass;
    private NativeDeferredLighting nativeVolumetric;
    //?}
    private boolean nativeScatterDrawn;

    private DeferredLightingPass() {
        super("Light");
    }

    @Override protected boolean skipUnderIris() {
        return !IrisCompat.canComposite();
    }

    public boolean hasNativeScattering() {
        return nativeScatterDrawn;
    }

    //? if >=26.2 {
    private NativeDeferredLighting nativePass() {
        if (nativePass == null) nativePass = new NativeDeferredLighting();
        return nativePass;
    }
    //?}

    public void renderSurface() {
        //? if >=26.2 {
        dispatchNative(() -> nativePass().render(null, frameCounter));
        //?} else {
        /*volumetricOnly = false;
        volumetricTarget = null;
        dispatch();
        *///?}
    }

    public void renderVolumetricRaw(Framebuffer target) {
        nativeScatterDrawn = false;
        if (!LightSettings.defaults().volumetric()) return;
        //? if >=26.2 {
        dispatchNative(() -> {
            if (nativeVolumetric == null) nativeVolumetric = new NativeDeferredLighting(GpuFormat.RGBA16_FLOAT);
            nativeScatterDrawn = nativeVolumetric.render(target, ++frameCounter);
        });
        //?} else {
        /*volumetricOnly = true;
        volumetricTarget = target;
        frameCounter++;
        dispatch();
        volumetricTarget = null;
        *///?}
    }

    public void render() {
        renderSurface();
    }

    @Override protected boolean enabled() {
        return !LightRegistry.INSTANCE.isEmpty()
                && (!IrisCompat.shadersActive() || IrisCompat.canComposite());
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

        boolean anyDirectional = volumes && anyDirectional(packed);
        boolean needsFullscreen = !volumes
                || volumetricOnly
                || debugMode != 0
                || anyDirectional
                || (!shaderPack && ShadowMapPass.INSTANCE.sunActive())
                || (!shaderPack && ShadingModelRegistry.INSTANCE.hasCustomModels());
        boolean reducedSurface = volumes && !needsFullscreen && count >= REDUCED_SURFACE_MIN_LIGHTS
                && LightSettings.defaults().surfaceScale() < 1f;
        if (reducedSurface) prepareSurfaceTarget();

        if (!volumetricOnly) capture.blitColorFromMain();
        capture.blitDepthFromMain();

        boolean toHalf = volumetricOnly && volumetricTarget != null;
        Framebuffer destination = toHalf ? volumetricTarget : reducedSurface ? surfaceTarget : null;
        int prevFbo = -1;
        if (destination != null) {
            destination.begin();
        } else {
            prevFbo = MainTargetFramebuffer.bind();
        }
        try {
            bindScene();
            boolean hasGBuffer = !shaderPack && GBufferTargets.INSTANCE.isPopulated();
            if (hasGBuffer) bindGBuffer();
            boolean shadows = !shaderPack && bindShadows();
            boolean sunShadows = !shaderPack && bindSunShadows();
            if (!shaderPack) GlState.bindTexture(LIGHTMAP_UNIT, levelLightmapGlId());
            lightBuffer.bind();
            if (!shaderPack) MaterialParams.INSTANCE.bind();

            program.begin();
            applyShared(program, cam, shadows, sunShadows, hasGBuffer, count, volumes);

            if (toHalf || debugMode == 0) {
                GlStateManager._disableBlend(0);
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (needsFullscreen) program.draw();

            if (volumes) {
                bindScene();
                LightVolumePass.INSTANCE.render(cam, packed,
                        reducedSurface ? surfaceTarget.width() : capture.width(),
                        reducedSurface ? surfaceTarget.height() : capture.height(),
                        p -> applyShared(p, cam, shadows, sunShadows, hasGBuffer, count, volumes));
            }
        } finally {
            unbind();
            if (destination != null) {
                destination.end();
            } else {
                MainTargetFramebuffer.restore(prevFbo);
            }
        }
        if (reducedSurface) compositeSurface(cam);
        return true;
    }

    private static boolean anyDirectional(List<Light> lights) {
        for (Light l : lights) {
            if (l.type() == LightType.DIRECTIONAL) return true;
        }
        return false;
    }

    private void prepareSurfaceTarget() {
        ensureSurfaceTarget(LightSettings.defaults().surfaceScale());
        surfaceTarget.begin();
        try {
            surfaceTarget.clear(0f, 0f, 0f, 0f);
        } finally {
            surfaceTarget.end();
        }
        surfaceTarget.blitDepthFromMain();
    }

    private void bindScene() {
        GlState.bindTexture(ALBEDO_UNIT, capture.colorTextureGlId(0));
        GlState.bindTexture(DEPTH_UNIT, capture.depthTextureGlId());
    }

    private static void bindGBuffer() {
        GlState.bindTexture(NORMAL_UNIT, GBufferTargets.INSTANCE.normalGlId());
        GlState.bindTexture(MATERIAL_UNIT, GBufferTargets.INSTANCE.materialGlId());
        GlState.bindTexture(GBUFFER_ALBEDO_UNIT, GBufferTargets.INSTANCE.albedoGlId());
        shade(GBufferTargets.INSTANCE.shadeGlId());
    }

    private static boolean bindShadows() {
        if (!ShadowMapPass.INSTANCE.isActive()) return false;
        GlState.bindTexture(SPOT_ATLAS_UNIT, SpotShadowAtlas.glTextureId());
        GlState.bindTexture(SPOT_COLOR_UNIT, SpotShadowAtlas.colorTextureId());
        bindUnit(POINT_SHADOW_UNIT, GL40.GL_TEXTURE_CUBE_MAP_ARRAY, PointShadowArray.glTextureId());
        return true;
    }

    private static boolean bindSunShadows() {
        if (!ShadowMapPass.INSTANCE.sunActive()) return false;
        bindUnit(SUN_SHADOW_UNIT, GL30.GL_TEXTURE_2D_ARRAY, SunShadowCascades.glTextureId());
        return true;
    }

    private static void bindUnit(int unit, int target, int texture) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(target, texture);
        GL33.glBindSampler(unit, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private void unbind() {
        GlState.bindTexture(SPOT_ATLAS_UNIT, 0);
        GlState.bindTexture(SPOT_COLOR_UNIT, 0);
        GlState.bindTexture(COOKIE_UNIT, 0);
        GlState.bindTexture(LIGHTMAP_UNIT, 0);
        GlState.bindTexture(GBUFFER_ALBEDO_UNIT, 0);
        shade(0);
        TexelBuffer.unbind(LightBuffer.UNIT);
        TexelBuffer.unbind(MaterialParams.UNIT);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + POINT_SHADOW_UNIT);
        GL11.glBindTexture(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, 0);
        GL33.glBindSampler(POINT_SHADOW_UNIT, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + SUN_SHADOW_UNIT);
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, 0);
        GL33.glBindSampler(SUN_SHADOW_UNIT, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private void ensureSurfaceTarget(float scale) {
        if (surfaceTarget != null && surfaceScale == scale) return;
        if (surfaceTarget != null) surfaceTarget.dispose();
        surfaceTarget = Framebuffers.screen("Local light accumulation", scale,
                FramebufferSpec.builder().color(ColorFormat.RGBA16F).depthTexture().build());
        surfaceScale = scale;
        if (surfaceUpscale == null) surfaceUpscale = new ShaderProgram(VSH, UPSCALE_FSH);
    }

    private void compositeSurface(CameraSnapshot cam) {
        int previous = MainTargetFramebuffer.bind();
        try {
            GlStateManager._disableDepthTest();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GlStateManager._depthMask(false);
            GL11.glDepthMask(false);
            GlStateManager._disableCull();
            GL11.glDisable(GL11.GL_CULL_FACE);
            GlStateManager._enableBlend(0);
            GL11.glEnable(GL11.GL_BLEND);
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
        } finally {
            MainTargetFramebuffer.restore(previous);
        }
    }

    private static void shade(int glId) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + SHADE_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
        GL33.glBindSampler(SHADE_UNIT, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    @Override
    protected void onDispose() {
        //? if >=26.2 {
        if (nativePass != null) {
            nativePass.close();
            nativePass = null;
        }
        if (nativeVolumetric != null) {
            nativeVolumetric.close();
            nativeVolumetric = null;
        }
        //?}
        if (lightBuffer != null) {
            lightBuffer.close();
            lightBuffer = null;
        }
        if (capture != null) {
            capture.dispose();
            capture = null;
        }
        if (surfaceTarget != null) {
            surfaceTarget.dispose();
            surfaceTarget = null;
        }
        if (surfaceUpscale != null) {
            surfaceUpscale.close();
            surfaceUpscale = null;
        }
        surfaceScale = -1f;
    }

    private static int levelLightmapGlId() {
        return VanillaCompat.levelLightmapGlId();
    }

    private int cookieGlId(Identifier id) {
        if (id == null) return 0;
        var tm = Minecraft.getInstance().getTextureManager();
        if (!ImportedTextures.isImported(id) && loadedCookies.add(id)) {
            try {
                VanillaCompat.loadTexture(tm, id);
            } catch (Throwable t) {
                return 0;
            }
        }
        return VanillaCompat.glId(tm.getTexture(id));
    }

    private void applyShared(ShaderProgram p, CameraSnapshot cam, boolean shadows, boolean sunShadows,
                             boolean hasGBuffer, int count, boolean volumes) {
        p.setSampler("AlbedoSampler", ALBEDO_UNIT);
        p.setSampler("DepthSampler", DEPTH_UNIT);
        p.setSampler("GNormalSampler", NORMAL_UNIT);
        p.setSampler("GMaterialSampler", MATERIAL_UNIT);
        p.setSampler("SpotShadowAtlas", SPOT_ATLAS_UNIT);
        p.setSampler("PointShadowArray", POINT_SHADOW_UNIT);
        p.setSampler("SpotShadowColor", SPOT_COLOR_UNIT);
        p.setSampler("LightmapSampler", LIGHTMAP_UNIT);
        p.setSampler("GAlbedoSampler", GBUFFER_ALBEDO_UNIT);
        p.setSampler("GShadeSampler", SHADE_UNIT);
        p.setSampler("LightTexels", LightBuffer.UNIT);
        p.setSampler("MaterialTexels", MaterialParams.UNIT);
        p.setInt("ShadowActive", shadows ? 1 : 0);
        if (shadows) applySpotShadows(p);
        p.setSampler("SunShadowMap", SUN_SHADOW_UNIT);
        p.setInt("SunShadowActive", sunShadows ? 1 : 0);
        if (sunShadows) applySunShadows(p);
        p.setInt("HasGBuffer", hasGBuffer ? 1 : 0);
        p.setInt("ShaderPackComposite", IrisCompat.compositing() ? 1 : 0);
        p.setInt("DepthReversed", IrisCompat.compositing() && IrisCompat.reverseDepth() ? 1 : 0);
        p.setMatrix4("InvViewProj", cam.invViewProj);
        p.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        p.setInt("LightCount", count);
        p.setFloat("LightTime", (float) ((System.nanoTime() / 1.0e9) % LIGHT_TIME_PERIOD));
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
        if (cookieGl != 0) GlState.bindTexture(COOKIE_UNIT, cookieGl);
        p.setSampler("CookieSampler", COOKIE_UNIT);
        p.setInt("HasCookie", cookieGl != 0 ? 1 : 0);
        p.setInt("DebugMode", debugMode);
        p.setInt("VolumetricOnly", volumetricOnly ? 1 : 0);
        p.setFloat("TemporalOffset", volumetricOnly ? (frameCounter & 7) / 8f : 0f);
    }

    private static void applySpotShadows(ShaderProgram p) {
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

    private static void applySunShadows(ShaderProgram p) {
        ShadowMapPass pass = ShadowMapPass.INSTANCE;
        p.setMatrix4Array("SunViewProj", pass.sunViewProj(), ShadowSettings.MAX_CASCADES);
        float[] radii = pass.sunCascadeRadius();
        for (int c = 0; c < ShadowSettings.MAX_CASCADES; c++) {
            p.setFloat("SunCascadeRadius[" + c + "]", radii[c]);
        }
        p.setInt("SunCascadeCount", pass.sunCascadeCount());
        p.setFloat("SunShadowRes", SunShadowCascades.resolution());
        p.setFloat("SunShadowDistance", pass.sunDistance());
    }
}
