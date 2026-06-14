package com.meekdev.amnetic.client;

import com.meekdev.amnetic.client.anim.Animations;
import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.compute.ComputeCapabilities;
import com.meekdev.amnetic.client.entityfx.EntityTextureOverride;
import com.meekdev.amnetic.client.entityfx.internal.EntityEffectRegistry;
import com.meekdev.amnetic.client.entityfx.internal.SceneColorSnapshot;
import com.meekdev.amnetic.client.entityfx.internal.TextureOverrideRegistry;
import com.meekdev.amnetic.client.framebuffer.internal.FramebufferRegistry;
import com.meekdev.amnetic.client.geometry.EntityMeshTap;
import com.meekdev.amnetic.client.geometry.internal.MeshTapRegistry;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.light.Lights;
import com.meekdev.amnetic.client.light.internal.DeferredLightingPass;
import com.meekdev.amnetic.client.model.internal.ModelRegistry;
import com.meekdev.amnetic.client.particle.ParticleSimulation;
import com.meekdev.amnetic.client.particle.Particles;
import com.meekdev.amnetic.client.particle.SceneDepth;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public class AmneticClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ComputeCapabilities.probeOnce();
            EntityEffectRegistry.INSTANCE.tick();
            TextureOverrideRegistry.INSTANCE.tick();
            MeshTapRegistry.INSTANCE.tick();
        });

        Particles.init();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
                FramebufferRegistry.INSTANCE.closeAll());

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            Lights.clear();
            DeferredLightingPass.INSTANCE.dispose();
            ModelRegistry.INSTANCE.closeAll();
            EntityEffectRegistry.INSTANCE.clear();
            SceneColorSnapshot.INSTANCE.dispose();
            EntityMeshTap.clear();
            EntityTextureOverride.clearAll();
        });

        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(ctx -> {
            if (CaptureManager.INSTANCE.isCapturing()) return;
            boolean captured = ParticleSimulation.INSTANCE.captureSceneDepth();
            MainTargetFramebuffer.setDepthOverride(captured ? SceneDepth.snapshotDepthGlId() : 0);
            if (!EntityEffectRegistry.INSTANCE.isEmpty()) {
                SceneColorSnapshot.INSTANCE.capture();
                EntityEffectRegistry.INSTANCE.uploadAll();
            }
        });

        LevelRenderEvents.END_MAIN.register(ctx -> {
            if (CaptureManager.INSTANCE.isCapturing()) return;
            Animations.update();
            InstanceMeshRegistry.INSTANCE.renderAll(InstancePhase.WORLD_LAST, ctx);
            ModelRegistry.INSTANCE.flush(ctx);
            MainTargetFramebuffer.setDepthOverride(0);
            DeferredLightingPass.INSTANCE.render();
            Bloom.render(ctx);
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
                    }
                }
        );
    }
}
