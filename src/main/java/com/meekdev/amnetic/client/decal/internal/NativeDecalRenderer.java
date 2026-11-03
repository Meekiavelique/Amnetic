package com.meekdev.amnetic.client.decal.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.decal.Decal;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.ImportedTextures;
import com.meekdev.amnetic.client.render.NativeScreenProgram;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class NativeDecalRenderer implements AutoCloseable {
    private static final int CACHE_SLACK = 64;
    private static final int WRITE_ALL = 15;
    private static final double BOUNDS_MARGIN = .002;
    private static final double LIGHT_OFFSET = .025;
    private static final float DEFAULT_ROUGHNESS = .7f;
    private static final Identifier COLOR_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/decal/decal.fsh");
    private static final Identifier MATERIAL_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/decal/decal_gbuffer.fsh");

    private static final class Visible {
        final Decal decal;
        final Matrix4f inverse = new Matrix4f();
        final Vector3f normal = new Vector3f();
        final Vector3f right = new Vector3f();
        final Vector3f up = new Vector3f();
        int x;
        int y;
        int width;
        int height;
        float block;
        float sky;
        long lightTick = Long.MIN_VALUE;
        Identifier textureId;
        Identifier normalId;
        GpuTextureView texture;
        GpuTextureView normalTexture;

        Visible(Decal decal) {
            this.decal = decal;
        }
    }

    private final IdentityHashMap<Decal, Visible> cache = new IdentityHashMap<>();
    private final Set<Decal> active = Collections.newSetFromMap(new IdentityHashMap<>());
    private ClientLevel level;
    private final BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();
    private final DecalBounds projectedBounds = new DecalBounds();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final ArrayList<Visible> visible = new ArrayList<>();
    private final Set<Identifier> loaded = new HashSet<>();
    private NativeScreenProgram color;
    private NativeScreenProgram material;
    private GpuTexture white;
    private GpuTextureView whiteView;
    private final Matrix4f model = new Matrix4f();
    private final int[] viewport = new int[4];
    private final int[] bounds = new int[4];

    private static ColorTargetState target(GpuFormat format, boolean write) {
        Optional<BlendFunction> blend = write ? Optional.of(BlendFunction.TRANSLUCENT) : Optional.empty();
        return new ColorTargetState(blend, format, write ? WRITE_ALL : 0);
    }

    void render(List<Decal> decals) {
        var mc = Minecraft.getInstance();
        var camera = CameraSnapshot.current();
        if (camera == null || mc.level == null) return;
        if (level != mc.level) {
            cache.clear();
            level = mc.level;
        }
        if (cache.size() > decals.size() + CACHE_SLACK) pruneCache(decals);
        var main = mc.gameRenderer.mainRenderTarget();
        viewport[2] = main.width;
        viewport[3] = main.height;
        visible.clear();
        frustum.set(camera.viewProj);
        for (var decal : decals) {
            Visible item = project(decal, camera, mc.level, main.width, main.height);
            if (item != null) visible.add(item);
        }
        if (visible.isEmpty()) return;
        ensurePrograms(main.getColorTexture().getFormat());
        ensureWhite();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var colorPass = RenderPassDescriptor.builder(() -> "Amnetic decal color")
                .withColorAttachment(main.getColorTextureView())
                .build();
        var gbuffer = GBufferTargets.INSTANCE;
        RenderPassDescriptor materials = gbuffer.isPopulated() ? materialPass(main.getColorTextureView(), gbuffer) : null;
        for (var item : visible) refreshTextures(item);
        color.prepare();
        configureCommon(color, camera, main.getDepthTextureView());
        color.setInt("TppLitWater", 0);
        color.setInt("TppWorldLit", 1);
        color.setTexture("TppLightmap", mc.gameRenderer.levelLightmap());
        try (var pass = encoder.createRenderPass(colorPass)) {
            var batch = color.batch(encoder, pass);
            for (var item : visible) {
                configure(color, item, false);
                batch.draw(p -> drawClipped(p, item));
            }
        }
        if (materials != null) {
            material.prepare();
            configureCommon(material, camera, main.getDepthTextureView());
            try (var pass = encoder.createRenderPass(materials)) {
                var batch = material.batch(encoder, pass);
                for (var item : visible) {
                    if (!item.decal.writesGBuffer()) continue;
                    configure(material, item, true);
                    batch.draw(p -> drawClipped(p, item));
                }
            }
        }
        encoder.submit();
    }

    private void pruneCache(List<Decal> decals) {
        active.clear();
        active.addAll(decals);
        cache.keySet().removeIf(d -> d.isRemoved() || !active.contains(d));
        active.clear();
    }

    private Visible project(Decal decal, CameraSnapshot camera, ClientLevel world, int width, int height) {
        if (decal.isRemoved() || decal.opacity() <= 0 || decal.width() <= 0 || decal.height() <= 0 || decal.depth() <= 0) {
            return null;
        }
        var center = decal.center();
        double extent = decal.width() * decal.width() + decal.height() * decal.height() + decal.depth() * decal.depth();
        double radius = .5 * Math.sqrt(extent) + BOUNDS_MARGIN;
        float cx = (float) (center.x - camera.eye.x);
        float cy = (float) (center.y - camera.eye.y);
        float cz = (float) (center.z - camera.eye.z);
        if (!frustum.testSphere(cx, cy, cz, (float) radius)) return null;
        var item = cache.computeIfAbsent(decal, Visible::new);
        var normal = item.normal.set(decal.normal()).normalize();
        var right = item.right.set(normal).cross(0, 1, 0);
        if (right.lengthSquared() < 1e-6f) right.set(1, 0, 0);
        right.normalize();
        var up = item.up.set(right).cross(normal).normalize();
        model.identity()
                .m00(right.x * decal.width()).m01(right.y * decal.width()).m02(right.z * decal.width())
                .m10(normal.x * decal.depth()).m11(normal.y * decal.depth()).m12(normal.z * decal.depth())
                .m20(up.x * decal.height()).m21(up.y * decal.height()).m22(up.z * decal.height())
                .m30((float) (center.x - camera.eye.x))
                .m31((float) (center.y - camera.eye.y))
                .m32((float) (center.z - camera.eye.z));
        if (!projectedBounds.compute(camera.viewProj, model, true, width, height)) return null;
        System.arraycopy(projectedBounds.rect(), 0, bounds, 0, 4);
        if (item.lightTick != world.getGameTime()) {
            lightPos.set(center.x + normal.x * LIGHT_OFFSET, center.y + normal.y * LIGHT_OFFSET, center.z + normal.z * LIGHT_OFFSET);
            item.block = (world.getBrightness(LightLayer.BLOCK, lightPos) + .5f) / 16;
            item.sky = (world.getBrightness(LightLayer.SKY, lightPos) + .5f) / 16;
            item.lightTick = world.getGameTime();
        }
        item.inverse.set(model).invert();
        item.x = bounds[0];
        item.y = bounds[1];
        item.width = bounds[2];
        item.height = bounds[3];
        return item;
    }

    private void ensurePrograms(GpuFormat sceneFormat) {
        if (color != null) return;
        color = new NativeScreenProgram(COLOR_FSH, target(sceneFormat, true));
        material = new NativeScreenProgram(MATERIAL_FSH,
                target(sceneFormat, false),
                target(GpuFormat.RGBA16_FLOAT, true),
                target(GpuFormat.RGBA8_UNORM, true),
                target(GpuFormat.RGBA16_FLOAT, false),
                target(GpuFormat.RGBA8_UNORM, true));
    }

    private void ensureWhite() {
        if (white != null) return;
        var device = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT;
        white = device.createTexture("Amnetic decal fallback", usage, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
        whiteView = device.createTextureView(white);
        var clear = device.createCommandEncoder();
        clear.clearColorTexture(white, new Vector4f(1));
        clear.submit();
    }

    private static RenderPassDescriptor materialPass(GpuTextureView scene, GBufferTargets gbuffer) {
        return RenderPassDescriptor.builder(() -> "Amnetic decal material")
                .withColorAttachment(scene)
                .withColorAttachment(gbuffer.nativeColorView(0))
                .withColorAttachment(gbuffer.nativeColorView(1))
                .withColorAttachment(gbuffer.nativeColorView(2))
                .withColorAttachment(gbuffer.nativeColorView(3))
                .build();
    }

    private void refreshTextures(Visible item) {
        if (item.texture == null || item.texture.isClosed() || !Objects.equals(item.textureId, item.decal.texture())) {
            item.textureId = item.decal.texture();
            item.texture = item.textureId == null ? whiteView : texture(item.textureId);
        }
        if (item.normalTexture == null || item.normalTexture.isClosed() || !Objects.equals(item.normalId, item.decal.normalMap())) {
            item.normalId = item.decal.normalMap();
            item.normalTexture = item.normalId == null ? whiteView : texture(item.normalId);
        }
    }

    private static void drawClipped(RenderPass pass, Visible item) {
        pass.enableScissor(item.x, item.y, item.width, item.height);
        pass.draw(3, 1, 0, 0);
    }

    private void configure(NativeScreenProgram shader, Visible item, boolean gbuffer) {
        var d = item.decal;
        shader.setMatrix("DecalInv", item.inverse);
        shader.setFloat("Opacity", d.opacity());
        shader.setFloat("AngleFade", d.angleFade());
        shader.setVector("DecalAxis", item.normal.x, item.normal.y, item.normal.z);
        var texture = item.texture;
        if (gbuffer) {
            shader.setTexture("NormalSampler", item.normalTexture);
            shader.setInt("HasNormalMap", d.normalMap() == null ? 0 : 1);
            shader.setTexture("TppWaterColor", texture);
            shader.setInt("TppHasColor", d.texture() == null ? 0 : 1);
            shader.setVector("TppTint", d.tint().x, d.tint().y, d.tint().z);
            shader.setVector("DecalRight", item.right.x, item.right.y, item.right.z);
            shader.setVector("DecalUp", item.up.x, item.up.y, item.up.z);
            shader.setFloat("Roughness", d.roughnessOr(DEFAULT_ROUGHNESS));
            shader.setFloat("Metallic", d.metallic());
            if (d.nearest()) {
                shader.setTextureNearest("NormalSampler", item.normalTexture);
                shader.setTextureNearest("TppWaterColor", texture);
            }
        } else {
            shader.setTexture("DecalSampler", texture);
            shader.setInt("HasTexture", d.texture() == null ? 0 : 1);
            shader.setVector("Tint", d.tint().x, d.tint().y, d.tint().z);
            shader.setVector("TppLightUV", item.block, item.sky);
            if (d.nearest()) shader.setTextureNearest("DecalSampler", texture);
        }
    }

    private void configureCommon(NativeScreenProgram shader, CameraSnapshot camera, GpuTextureView depth) {
        shader.setTexture("DepthSampler", depth);
        shader.setMatrix("InvViewProj", camera.invViewProj);
        shader.setInt("ZeroToOne", camera.zeroToOne ? 1 : 0);
    }

    private GpuTextureView texture(Identifier id) {
        var textures = Minecraft.getInstance().getTextureManager();
        if (!ImportedTextures.isImported(id) && loaded.add(id)) VanillaCompat.loadTexture(textures, id);
        return textures.getTexture(id).getTextureView();
    }

    @Override public void close() {
        if (color != null) color.close();
        if (material != null) material.close();
        if (whiteView != null) whiteView.close();
        if (white != null) white.close();
        whiteView = null;
        white = null;
        color = null;
        material = null;
        loaded.clear();
        visible.clear();
        cache.clear();
        level = null;
    }
}
