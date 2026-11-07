package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.compat.IrisCompat;
import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.shadow.ShadowSettings;
import com.meekdev.amnetic.client.shadow.internal.NativeLocalShadows;
import org.joml.Matrix4f;
import com.meekdev.amnetic.client.framebuffer.*;
import com.meekdev.amnetic.client.framebuffer.internal.NativeFramebuffer;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.light.LightSettings;
import com.meekdev.amnetic.client.material.internal.MaterialParams;
import com.meekdev.amnetic.client.material.internal.ShadingModelRegistry;
import com.meekdev.amnetic.client.render.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.textures.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.FrustumIntersection;
import org.joml.Vector4f;
import java.util.*;

final class NativeDeferredLighting implements AutoCloseable {
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("amnetic", "shaders/light/deferred.fsh");
    private static final double LIGHT_TIME_WRAP_SECONDS = 3600;

    private final GpuFormat outputFormat;
    private final NativeFramebuffer capture = new NativeFramebuffer(
            FramebufferSpec.builder().color(ColorFormat.RGBA8).build(), "Amnetic lighting snapshot");
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Set<Identifier> loadedCookies = new HashSet<>();
    private LightBuffer lights;
    private int capacity;
    private NativeScreenProgram program;
    private GpuTexture white;
    private GpuTextureView whiteView;

    NativeDeferredLighting() {
        this(GpuFormat.RGBA8_UNORM);
    }

    NativeDeferredLighting(GpuFormat outputFormat) {
        this.outputFormat = outputFormat;
    }

    boolean render(Framebuffer volumetricTarget, int frame) {
        var cam = CameraSnapshot.current();
        if (cam == null || LightRegistry.INSTANCE.isEmpty()) return false;
        var settings = LightSettings.defaults();
        ensureLightBuffer(settings.maxLights());
        frustum.set(cam.viewProj);
        boolean volumetric = volumetricTarget != null;
        int count = volumetric
                ? ScatteringLights.pack(lights, LightRegistry.INSTANCE.all(), cam.eye, frustum)
                : TiledLighting.pack(lights, LightRegistry.INSTANCE.all(), cam.eye, frustum, null);
        if (count == 0) return false;
        if (ShadingModelRegistry.INSTANCE.consumeDirty()) closeProgram();
        ensure();
        var mc = Minecraft.getInstance();
        var main = mc.gameRenderer.mainRenderTarget();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        if (!volumetric) {
            capture.allocate(main.width, main.height);
            encoder.copyTextureToTexture(main.getColorTexture(), capture.color(0), 0, 0, 0, 0, 0, main.width, main.height);
        }
        bindScene(main.getDepthTextureView(), volumetric);
        bindShadows();
        bindLights(volumetric);
        bindCookie(settings);
        bindSettings(cam, settings, count, volumetric, frame);
        var descriptor = volumetric
                ? volumetricTarget.renderPassDescriptor()
                : RenderPassDescriptor.builder(() -> "Amnetic deferred lights").withColorAttachment(main.getColorTextureView()).build();
        program.draw(encoder, descriptor, pass -> pass.draw(3, 1, 0, 0));
        encoder.submit();
        return true;
    }

    private void ensureLightBuffer(int maxLights) {
        if (lights != null && capacity == maxLights) return;
        if (lights != null) lights.close();
        capacity = maxLights;
        lights = new LightBuffer(capacity);
    }

    private void bindScene(GpuTextureView depth, boolean volumetric) {
        var g = GBufferTargets.INSTANCE;
        boolean gbuffer = g.isPopulated() && !IrisCompat.shadersActive();
        program.setTexture("AlbedoSampler", volumetric ? whiteView : capture.colorView(0));
        program.setTexture("DepthSampler", depth);
        program.setTexture("GNormalSampler", gbuffer ? g.nativeColorView(0) : whiteView);
        program.setTexture("GMaterialSampler", gbuffer ? g.nativeColorView(1) : whiteView);
        program.setTexture("GShadeSampler", whiteView);
        program.setTexture("GAlbedoSampler", gbuffer ? g.nativeColorView(3) : whiteView);
        var lightmap = Minecraft.getInstance().gameRenderer.levelLightmap();
        program.setTexture("LightmapSampler", lightmap != null ? lightmap : whiteView);
        program.setInt("HasGBuffer", gbuffer ? 1 : 0);
    }

    private void bindShadows() {
        var shadows = NativeLocalShadows.INSTANCE;
        program.setTexture("SpotShadowAtlas", whiteView);
        program.setTexture("SpotShadowColor", whiteView);
        program.setTexture("PointShadowArray", whiteView);
        program.setTexture("SunShadowMap", whiteView);
        if (shadows.sunDepth() != null) program.setTexture("SunShadowMap", shadows.sunDepth());
        if (shadows.pointDepth() != null) program.setTexture("PointShadowArray", shadows.pointDepth());
        if (shadows.spotDepth() != null) {
            program.setTexture("SpotShadowAtlas", shadows.spotDepth());
            program.setTexture("SpotShadowColor", shadows.spotColor());
        }
        program.setInt("PointShadowRows", Math.max(1, shadows.rows));
        program.setInt("PointShadowColumns", Math.max(1, shadows.columns));
        program.setInt("ShadowActive", shadows.points > 0 || shadows.spots > 0 || shadows.sunCascades > 0 ? 1 : 0);
        var ss = ShadowSettings.defaults();
        for (int i = 0; i < shadows.spots; i++) {
            program.setMatrix("SpotViewProj[" + i + "]", new Matrix4f().set(shadows.spotMatrices, i * 16));
        }
        program.setFloat("ShadowAtlasSize", ss.resolution() * shadows.spotGrid);
        program.setFloat("ShadowTileSize", ss.resolution());
        program.setInt("ShadowGridX", shadows.spotGrid);
        program.setFloat("ShadowBias", ss.bias());
        program.setFloat("ShadowNormalBias", ss.normalBias());
        program.setFloat("ShadowSoftnessTexels", ss.softness());
        program.setFloat("ShadowFadeStart", ss.fadeStartDistance());
        program.setFloat("ShadowFadeEnd", ss.maxDistance());
        program.setInt("ShadowPcss", ss.pcss() ? 1 : 0);
        program.setFloat("ShadowLightSize", ss.lightSize());
        program.setInt("SunShadowActive", shadows.sunCascades > 0 ? 1 : 0);
        program.setInt("SunCascadeCount", shadows.sunCascades);
        program.setFloat("SunShadowRes", ss.sunResolution());
        program.setFloat("SunShadowDistance", ss.sunDistance());
        for (int i = 0; i < shadows.sunCascades; i++) {
            program.setMatrix("SunViewProj[" + i + "]", new Matrix4f().set(shadows.sunMatrices, i * 16));
            program.setFloat("SunCascadeRadius[" + i + "]", shadows.sunRadii[i]);
        }
    }

    private void bindLights(boolean volumetric) {
        program.setTexels("LightTexels", lights.nativeSlice());
        program.setTexture("LocalLightSampler", whiteView);
        program.setTexels("TileLights", lights.nativeSlice());
        TiledLighting.bind(program, !volumetric);
        ScatteringLights.bind(program, volumetric);
        program.setTexels("MaterialTexels", MaterialParams.INSTANCE.nativeSlice());
    }

    private void bindCookie(LightSettings settings) {
        var textures = Minecraft.getInstance().getTextureManager();
        GpuTextureView cookie = whiteView;
        var id = settings.cookieTexture();
        if (id != null) {
            if (!ImportedTextures.isImported(id) && loadedCookies.add(id)) VanillaCompat.loadTexture(textures, id);
            var texture = textures.getTexture(id);
            if (texture != null && texture.getTextureView() != null) cookie = texture.getTextureView();
        }
        program.setTexture("CookieSampler", cookie);
        program.setInt("HasCookie", cookie == whiteView ? 0 : 1);
    }

    private void bindSettings(CameraSnapshot cam, LightSettings settings, int count, boolean volumetric, int frame) {
        program.setMatrix("InvViewProj", cam.invViewProj);
        program.setMatrix("ViewProj", cam.viewProj);
        program.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        program.setInt("LightCount", count);
        program.setFloat("LightTime", (float) ((System.nanoTime() / 1.0e9) % LIGHT_TIME_WRAP_SECONDS));
        program.setInt("SkipLocalLights", 0);
        program.setInt("DirectionalAdditive", 1);
        program.setInt("VolumetricSteps", settings.volumetricSteps());
        program.setFloat("VolumetricStrength", settings.volumetricStrength());
        program.setFloat("VolumetricDensity", settings.volumetricDensity());
        program.setFloat("VolumetricAniso", settings.volumetricAniso());
        program.setInt("VolumetricShadows", settings.volumetricShadows() ? 1 : 0);
        program.setInt("ContactSteps", settings.contactSteps());
        program.setFloat("ContactDistance", settings.contactDistance());
        program.setFloat("ContactThickness", settings.contactThickness());
        program.setInt("DebugMode", DeferredLightingPass.debugMode);
        program.setInt("VolumetricOnly", volumetric ? 1 : 0);
        program.setFloat("TemporalOffset", volumetric ? (frame & 7) / 8f : 0);
    }

    private void ensure() {
        if (program == null) {
            var target = new ColorTargetState(Optional.empty(), outputFormat, ColorTargetState.WRITE_ALL);
            program = new NativeScreenProgram(SHADER, Set.of(ShaderSwitches.NATIVE), target);
        }
        if (white != null) return;
        var device = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT;
        try {
            white = device.createTexture("Amnetic white fallback", usage, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
            whiteView = device.createTextureView(white);
            var encoder = device.createCommandEncoder();
            encoder.clearColorTexture(white, new Vector4f(1));
            encoder.submit();
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    private void closeProgram() {
        if (program == null) return;
        program.close();
        program = null;
    }

    @Override public void close() {
        closeProgram();
        if (lights != null) {
            lights.close();
            lights = null;
        }
        capture.close();
        if (whiteView != null) {
            whiteView.close();
            whiteView = null;
        }
        if (white != null) {
            white.close();
            white = null;
        }
    }
}
