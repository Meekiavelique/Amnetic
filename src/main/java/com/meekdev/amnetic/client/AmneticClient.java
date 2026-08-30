package com.meekdev.amnetic.client;

import com.meekdev.amnetic.client.anim.Animations;
import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.compute.ComputeCapabilities;
import com.meekdev.amnetic.client.decal.Decals;
import com.meekdev.amnetic.client.dev.PostShaderReload;
import com.meekdev.amnetic.client.dev.ShaderHotReload;
import com.meekdev.amnetic.client.entityfx.EntityTextureOverride;
import com.meekdev.amnetic.client.entityfx.internal.EntityEffectRegistry;
import com.meekdev.amnetic.client.entityfx.internal.SceneColorSnapshot;
import com.meekdev.amnetic.client.entityfx.internal.TextureOverrideRegistry;
import com.meekdev.amnetic.client.framebuffer.internal.FramebufferRegistry;
import com.meekdev.amnetic.client.gbuffer.GBuffer;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferNormalFill;
import com.meekdev.amnetic.client.geometry.EntityMeshTap;
import com.meekdev.amnetic.client.geometry.internal.MeshTapRegistry;
import com.meekdev.amnetic.client.grade.ColorGrade;
import com.meekdev.amnetic.client.ibl.internal.EnvProbe;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.light.LightStyles;
import com.meekdev.amnetic.client.light.Lights;
import com.meekdev.amnetic.client.light.internal.DeferredLightingPass;
import com.meekdev.amnetic.client.light.internal.VolumetricPass;
import com.meekdev.amnetic.client.material.internal.ShadingModelRegistry;
import com.meekdev.amnetic.client.model.HandModels;
import com.meekdev.amnetic.client.model.ItemModels;
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ViewModels;
import com.meekdev.amnetic.client.model.WorldModels;
import com.meekdev.amnetic.client.model.internal.GlUploadQueue;
import com.meekdev.amnetic.client.model.internal.ModelRegistry;
import com.meekdev.amnetic.client.model.internal.ammesh.AmmeshScanner;
import com.meekdev.amnetic.client.particle.ParticleSimulation;
import com.meekdev.amnetic.client.particle.Particles;
import com.meekdev.amnetic.client.particle.SceneDepth;
import com.meekdev.amnetic.client.pipeline.FrameContext;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.pipeline.internal.LayerSystem;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import com.meekdev.amnetic.client.render.AmneticResources;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.Geometry;
import com.meekdev.amnetic.client.render.ImageOps;
import com.meekdev.amnetic.client.render.OverlayRender;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import com.meekdev.amnetic.client.shadow.Shadows;
import com.meekdev.amnetic.client.shadow.internal.ShadowMapPass;
import com.meekdev.amnetic.client.ssao.Ssao;
import com.meekdev.amnetic.client.subsurface.Subsurface;
import com.meekdev.amnetic.client.ssgi.Ssgi;
import com.meekdev.amnetic.client.ssr.Ssr;
import com.meekdev.amnetic.client.surface.reactive.Reactive;
import com.meekdev.amnetic.client.taa.Taa;
import com.meekdev.amnetic.client.ui.AmneticEditorBridge;
import net.minecraft.client.Minecraft;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public class AmneticClient implements ClientModInitializer {

    private static LevelRenderContext pendingPostCtx;

    public static void renderPost() {
        LevelRenderContext ctx = pendingPostCtx;
        pendingPostCtx = null;
        if (ctx == null || CaptureManager.INSTANCE.isCapturing()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getMainRenderTarget() != null
                && (PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.PRE_GUI)
                    || PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.POST_RENDER))) {
            PostEffectRegistry.INSTANCE.captureWorldDepthSnapshot(mc.getMainRenderTarget());
        }

        int snapshotDepth = SceneDepth.snapshotDepthGlId();
        FrameContext fc = new FrameContext(CameraSnapshot.current(), ctx, snapshotDepth);

        if (snapshotDepth > 0) MainTargetFramebuffer.setDepthOverride(snapshotDepth);
        Pipeline.runStage(RenderStage.AFTER_WATER, fc);
        MainTargetFramebuffer.setDepthOverride(0);

        Pipeline.runStage(RenderStage.ATMOSPHERE, fc);
        Pipeline.runStage(RenderStage.POST, fc);
    }

    private static void registerDefaultPasses() {
        Pipeline.add(RenderStage.GEOMETRY, 10, "GBuffer Normal Fill", ctx -> GBufferNormalFill.INSTANCE.render());
        Pipeline.add(RenderStage.GEOMETRY, 20, "Instanced Mesh (GBuffer)", ctx -> InstanceMeshRegistry.INSTANCE.renderGBuffer(InstancePhase.WORLD_LAST, ctx.fabric()));
        Pipeline.add(RenderStage.GEOMETRY, 30, "Instanced Mesh (World)", ctx -> InstanceMeshRegistry.INSTANCE.renderAll(InstancePhase.WORLD_LAST, ctx.fabric()));
        Pipeline.add(RenderStage.GEOMETRY, 40, "Models", ctx -> ModelRegistry.INSTANCE.flush(ctx.fabric()));
        Pipeline.add(RenderStage.GEOMETRY, 50, "Decals", ctx -> Decals.render());

        Pipeline.add(RenderStage.LIGHTING, 10, "Shadow Map", ctx -> ShadowMapPass.INSTANCE.render(ctx.camera()));
        Pipeline.add(RenderStage.LIGHTING, 20, "Deferred Lighting", ctx -> DeferredLightingPass.INSTANCE.renderSurface());

        Pipeline.add(RenderStage.SCREEN_SPACE, 5, "Subsurface", ctx -> Subsurface.render());
        Pipeline.add(RenderStage.SCREEN_SPACE, 10, "SSAO", ctx -> Ssao.render());
        Pipeline.add(RenderStage.SCREEN_SPACE, 20, "SSGI", ctx -> Ssgi.render());
        Pipeline.add(RenderStage.SCREEN_SPACE, 30, "SSR", ctx -> Ssr.render());

        Pipeline.add(RenderStage.AFTER_WATER, 10, "Instanced Mesh (Translucent)", ctx -> InstanceMeshRegistry.INSTANCE.renderAll(InstancePhase.WORLD_TRANSLUCENT, ctx.fabric()));

        Pipeline.add(RenderStage.ATMOSPHERE, 10, "Volumetric", ctx -> VolumetricPass.INSTANCE.render());

        Pipeline.add(RenderStage.POST, 5, "TAA", ctx -> Taa.render());
        Pipeline.add(RenderStage.POST, 10, "Bloom", ctx -> Bloom.render(ctx.fabric()));
        Pipeline.add(RenderStage.POST, 20, "Color Grade", ctx -> ColorGrade.render());
        Pipeline.add(RenderStage.POST, 30, "CAS Sharpen", ctx -> Taa.renderSharpen());

        Pipeline.add(RenderStage.OVERLAY, 10, "Overlay", ctx -> OverlayRender.render());
    }

    @Override
    public void onInitializeClient() {
        LightStyles.touch();
        ShadingModelRegistry.touch();

        ShaderHotReload.watchAllDevMods();
        PostShaderReload.install();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ComputeCapabilities.probeOnce();
            EntityEffectRegistry.INSTANCE.tick();
            TextureOverrideRegistry.INSTANCE.tick();
            MeshTapRegistry.INSTANCE.tick();
        });

        Particles.init();

        AmneticEditorBridge.init();

        AmneticResources.register(() -> FramebufferRegistry.INSTANCE.closeAll());
        AmneticResources.register(Lights::clear);
        AmneticResources.register(DeferredLightingPass.INSTANCE::dispose);
        AmneticResources.register(VolumetricPass.INSTANCE::dispose);
        AmneticResources.register(Ssr::dispose);
        AmneticResources.register(Taa::dispose);
        AmneticResources.register(Ssao::dispose);
        AmneticResources.register(Ssgi::dispose);
        AmneticResources.register(EnvProbe.INSTANCE::dispose);
        AmneticResources.register(LayerSystem.INSTANCE::dispose);
        AmneticResources.register(GBufferNormalFill.INSTANCE::dispose);
        AmneticResources.register(GBuffer::dispose);
        AmneticResources.register(Shadows::dispose);
        AmneticResources.register(ColorGrade::dispose);
        AmneticResources.register(Decals::clear);
        AmneticResources.register(Decals::dispose);
        AmneticResources.register(WorldModels::clear);
        AmneticResources.register(ItemModels::clear);
        AmneticResources.register(HandModels::dispose);
        AmneticResources.register(ViewModels::dispose);
        AmneticResources.register(Geometry::dispose);
        AmneticResources.register(ImageOps::dispose);
        AmneticResources.register(Model::disposeShared);
        AmneticResources.register(ModelRegistry.INSTANCE::closeAll);
        AmneticResources.register(EntityEffectRegistry.INSTANCE::clear);
        AmneticResources.register(SceneColorSnapshot.INSTANCE::dispose);
        AmneticResources.register(EntityMeshTap::clear);
        AmneticResources.register(EntityTextureOverride::clearAll);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> AmneticResources.disposeAll());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            WorldModels.clear();
            ItemModels.clear();
            Decals.clear();
            Lights.clear();
        });

        registerDefaultPasses();

        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(ctx -> {
            if (CaptureManager.INSTANCE.isCapturing()) return;
            boolean captured = ParticleSimulation.INSTANCE.captureSceneDepth();
            int snapshot = captured ? SceneDepth.snapshotDepthGlId() : 0;
            MainTargetFramebuffer.setDepthOverride(snapshot);
            if (!EntityEffectRegistry.INSTANCE.isEmpty()) {
                SceneColorSnapshot.INSTANCE.capture();
                EntityEffectRegistry.INSTANCE.uploadAll();
            }
            Pipeline.runStage(RenderStage.SETUP, new FrameContext(CameraSnapshot.current(), ctx, snapshot));
        });

        LevelRenderEvents.END_MAIN.register(ctx -> {
            pendingPostCtx = null;
            if (CaptureManager.INSTANCE.isCapturing()) return;
            ModelRegistry.INSTANCE.warmup();
            GlUploadQueue.drain(2_000_000L);
            Animations.update();
            Reactive.tick(surfaceDt());
            GBuffer.beginFrame();
            FrameContext fc = new FrameContext(CameraSnapshot.current(), ctx, SceneDepth.snapshotDepthGlId());
            Pipeline.runStage(RenderStage.GEOMETRY, fc);
            MainTargetFramebuffer.setDepthOverride(0);
            Pipeline.runStage(RenderStage.LIGHTING, fc);
            Pipeline.runStage(RenderStage.SCREEN_SPACE, fc);
            pendingPostCtx = ctx;
        });

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.fromNamespaceAndPath("amnetic", "post_effect_cache");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        PostEffectRegistry.INSTANCE.invalidatePipelineCaches();
                        AmmeshScanner.scanAsync();
                    }
                }
        );
    }

    private static long surfaceLastNano;

    private static float surfaceDt() {
        long now = System.nanoTime();
        float dt = surfaceLastNano == 0L ? 0f : (now - surfaceLastNano) / 1.0e9f;
        surfaceLastNano = now;
        return dt;
    }
}
