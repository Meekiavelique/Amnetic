package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
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

/** Deferred composition with native scene snapshots, material texels, and light texels. */
final class NativeDeferredLighting implements AutoCloseable {
    private final GpuFormat outputFormat;
    NativeDeferredLighting() { this(GpuFormat.RGBA8_UNORM); }
    NativeDeferredLighting(GpuFormat outputFormat) { this.outputFormat = outputFormat; }
    private final NativeFramebuffer capture = new NativeFramebuffer(
            FramebufferSpec.builder().color(ColorFormat.RGBA8).build(), "Amnetic lighting snapshot");
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Set<Identifier> loadedCookies = new HashSet<>();
    private LightBuffer lights;
    private int capacity;
    private NativeScreenProgram program;
    private boolean reversed = true;
    private GpuTexture white;
    private GpuTextureView whiteView;

    boolean render(Framebuffer volumetricTarget, int frame) {
        var cam = CameraSnapshot.current();
        if (cam == null || LightRegistry.INSTANCE.isEmpty()) return false;
        var settings = LightSettings.defaults();
        if (lights == null || capacity != settings.maxLights()) {
            if (lights != null) lights.close();
            capacity = settings.maxLights();
            lights = new LightBuffer(capacity);
        }
        frustum.set(cam.viewProj);
        int count = volumetricTarget!=null ? ScatteringLights.pack(lights,LightRegistry.INSTANCE.all(),cam.eye,frustum)
                : TiledLighting.pack(lights,LightRegistry.INSTANCE.all(),cam.eye,frustum,null);
        if (count == 0) return false;
        if (ShadingModelRegistry.INSTANCE.consumeDirty() && program != null) { program.close(); program = null; }
        boolean reverse=com.meekdev.amnetic.client.compat.IrisCompat.reverseDepth();
        if(reverse!=reversed && program!=null){program.close();program=null;}
        reversed=reverse;
        ensure();
        var mc = Minecraft.getInstance();
        var main = mc.gameRenderer.mainRenderTarget();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        if(volumetricTarget==null) {
            capture.allocate(main.width, main.height);
            encoder.copyTextureToTexture(main.getColorTexture(), capture.color(0), 0, 0, 0, 0, 0, main.width, main.height);
        }
        var g = GBufferTargets.INSTANCE;
        program.setTexture("AlbedoSampler", volumetricTarget==null?capture.colorView(0):whiteView);
        program.setTexture("DepthSampler", main.getDepthTextureView());
        program.setTexture("GNormalSampler", g.isPopulated() && !com.meekdev.amnetic.client.compat.IrisCompat.shadersActive() ? g.nativeColorView(0) : whiteView);
        program.setTexture("GMaterialSampler", g.isPopulated() && !com.meekdev.amnetic.client.compat.IrisCompat.shadersActive() ? g.nativeColorView(1) : whiteView);
        program.setTexture("GShadeSampler", whiteView);
        program.setTexture("GAlbedoSampler", g.isPopulated() && !com.meekdev.amnetic.client.compat.IrisCompat.shadersActive() ? g.nativeColorView(3) : whiteView);
        var lightmap = mc.gameRenderer.levelLightmap();
        program.setTexture("LightmapSampler", lightmap != null ? lightmap : whiteView);
        program.setTexture("SpotShadowAtlas", whiteView);
        program.setTexture("SpotShadowColor", whiteView);
        program.setTexture("PointShadowArray", whiteView);
        program.setTexture("SunShadowMap", whiteView);
        var shadows = com.meekdev.amnetic.client.shadow.internal.NativeLocalShadows.INSTANCE;
        if (shadows.sunDepth() != null) program.setTexture("SunShadowMap", shadows.sunDepth());
        if (shadows.pointDepth() != null) program.setTexture("PointShadowArray", shadows.pointDepth());
        if (shadows.spotDepth() != null) {
            program.setTexture("SpotShadowAtlas", shadows.spotDepth());
            program.setTexture("SpotShadowColor", shadows.spotColor());
        }
        program.setInt("PointShadowRows", Math.max(1, shadows.rows));
        program.setTexels("LightTexels", lights.nativeSlice());
        program.setTexture("LocalLightSampler",whiteView);
        program.setTexels("TileLights",lights.nativeSlice());
        TiledLighting.bind(program,volumetricTarget==null);
        ScatteringLights.bind(program,volumetricTarget!=null);
        program.setTexels("MaterialTexels", MaterialParams.INSTANCE.nativeSlice());
        GpuTextureView cookie = whiteView;
        if (settings.cookieTexture() != null) {
            var id = settings.cookieTexture();
            if (!ImportedTextures.isImported(id) && loadedCookies.add(id)) VanillaCompat.loadTexture(mc.getTextureManager(), id);
            var texture = mc.getTextureManager().getTexture(id);
            if (texture != null && texture.getTextureView() != null) cookie = texture.getTextureView();
        }
        program.setTexture("CookieSampler", cookie);
        program.setInt("HasCookie", cookie == whiteView ? 0 : 1);
        program.setInt("HasGBuffer", g.isPopulated() && !com.meekdev.amnetic.client.compat.IrisCompat.shadersActive() ? 1 : 0);
        program.setInt("ShadowActive", shadows.points > 0 || shadows.spots > 0 || shadows.sunCascades > 0 ? 1 : 0);
        var ss = com.meekdev.amnetic.client.shadow.ShadowSettings.defaults();
        for (int i = 0; i < shadows.spots; i++)
            program.setMatrix("SpotViewProj[" + i + "]", new org.joml.Matrix4f().set(shadows.spotMatrices, i * 16));
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
        program.setInt("SunShadowActive", shadows.sunCascades>0?1:0);
        program.setInt("SunCascadeCount",shadows.sunCascades);
        program.setFloat("SunShadowRes",ss.sunResolution());
        program.setFloat("SunShadowDistance",ss.sunDistance());
        for(int i=0;i<shadows.sunCascades;i++){
            program.setMatrix("SunViewProj["+i+"]",new org.joml.Matrix4f().set(shadows.sunMatrices,i*16));
            program.setFloat("SunCascadeRadius["+i+"]",shadows.sunRadii[i]);
        }
        program.setMatrix("InvViewProj", cam.invViewProj);
        program.setMatrix("ViewProj", cam.viewProj);
        program.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        program.setInt("LightCount", count);
        program.setFloat("LightTime", (float) ((System.nanoTime() / 1.0e9) % 3600));
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
        program.setInt("VolumetricOnly", volumetricTarget == null ? 0 : 1);
        program.setFloat("TemporalOffset", volumetricTarget == null ? 0 : (frame & 7) / 8f);
        var descriptor = volumetricTarget == null
                ? RenderPassDescriptor.builder(() -> "Amnetic deferred lights").withColorAttachment(main.getColorTextureView()).build()
                : volumetricTarget.renderPassDescriptor();
        program.draw(encoder, descriptor, pass -> pass.draw(3, 1, 0, 0));
        encoder.submit();
        return true;
    }

    private void ensure() {
        if (program == null) program = new NativeScreenProgram(
                Identifier.fromNamespaceAndPath("amnetic", "shaders/light/deferred.fsh"),
                source -> nativeSource(source, reversed),
                new ColorTargetState(Optional.empty(), outputFormat, ColorTargetState.WRITE_ALL));
        if (white != null) return;
        var device = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT;
        try {
            white = device.createTexture("Amnetic white fallback", usage, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
            whiteView = device.createTextureView(white);
            var encoder = device.createCommandEncoder();
            encoder.clearColorTexture(white, new Vector4f(1));
            encoder.submit();
        } catch (RuntimeException failure) { close(); throw failure; }
    }

    private static String nativeSource(String source, boolean reversed) {
        // RenderPearl 26.3 does not expose array textures. Store cube faces and cascades in 2D atlases.
        if (reversed) source = source.replace("depth >= 1.0", "depth <= 0.0").replace("sd < 1.0", "sd > 0.0");
        return source
                .replace("vec4(texture(AlbedoSampler, vUV).rgb, 1.0)","texture(AlbedoSampler, vUV)")
                .replace("FragColor = vec4(outColor, 1.0);","FragColor = vec4(outColor, texture(AlbedoSampler, vUV).a);")
                .replace("* 0.0025 * ze", "* (2.0 / max(float(textureSize(PointShadowArray, 0).x) / 6.0, 1.0)) * ze")
                .replace("uniform samplerCubeArray PointShadowArray;", """
                        uniform sampler2D PointShadowArray;
                        uniform int PointShadowRows;
                        float amneticPointDepth(vec3 d, int slot) {
                            vec3 a = abs(d); vec2 uv; int face;
                            if (a.x >= a.y && a.x >= a.z) {
                                face = d.x >= 0.0 ? 0 : 1;
                                uv = vec2(d.x >= 0.0 ? -d.z : d.z, -d.y) / max(a.x, 1e-6);
                            } else if (a.y >= a.z) {
                                face = d.y >= 0.0 ? 2 : 3;
                                uv = vec2(d.x, d.y >= 0.0 ? d.z : -d.z) / max(a.y, 1e-6);
                            } else {
                                face = d.z >= 0.0 ? 4 : 5;
                                uv = vec2(d.z >= 0.0 ? d.x : -d.x, -d.y) / max(a.z, 1e-6);
                            }
                            uv = uv * 0.5 + 0.5;
                            vec2 size = vec2(textureSize(PointShadowArray, 0));
                            vec2 grid = vec2(6.0, float(max(PointShadowRows, 1)));
                            vec2 tileSize = size / grid;
                            uv = clamp(uv, 0.5 / tileSize, 1.0 - 0.5 / tileSize);
                            return texture(PointShadowArray, (uv + vec2(float(face), float(slot))) / grid).r;
                        }
                        """)
                .replace("uniform sampler2DArray SunShadowMap;", "uniform sampler2D SunShadowMap;")
                .replace("texture(PointShadowArray, vec4(sampleDir, float(slot))).r", "amneticPointDepth(sampleDir, slot)")
                .replace("texture(PointShadowArray, vec4(d, float(slot))).r", "amneticPointDepth(d, slot)")
                .replace("texture(SunShadowMap, vec3(suv, float(c)))", "texture(SunShadowMap, (suv + vec2(float(c), 0.0)) / vec2(4.0, 1.0))")
                .replace("texture(SunShadowMap, vec3(ndc.xy * 0.5 + 0.5, float(c)))",
                        "texture(SunShadowMap, (ndc.xy * 0.5 + 0.5 + vec2(float(c), 0.0)) / vec2(4.0, 1.0))");
    }

    @Override public void close() {
        if (program != null) { program.close(); program = null; }
        if (lights != null) { lights.close(); lights = null; }
        capture.close();
        if (whiteView != null) { whiteView.close(); whiteView = null; }
        if (white != null) { white.close(); white = null; }
    }
}
