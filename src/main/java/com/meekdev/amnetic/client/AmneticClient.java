package com.meekdev.amnetic.client;

import com.meekdev.amnetic.client.anim.Animations;
import com.meekdev.amnetic.client.compute.ComputeCapabilities;
import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.framebuffer.internal.FramebufferRegistry;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.particle.ParticleSimulation;
import com.meekdev.amnetic.client.particle.Particles;
import com.meekdev.amnetic.client.particle.SceneDepth;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
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
            com.meekdev.amnetic.client.entityfx.internal.EntityEffectRegistry.INSTANCE.tick();
            com.meekdev.amnetic.client.entityfx.internal.TextureOverrideRegistry.INSTANCE.tick();
            com.meekdev.amnetic.client.geometry.internal.MeshTapRegistry.INSTANCE.tick();
        });

        Particles.init();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
                FramebufferRegistry.INSTANCE.closeAll());

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            com.meekdev.amnetic.client.light.Lights.clear();
            com.meekdev.amnetic.client.light.internal.DeferredLightingPass.INSTANCE.dispose();
            com.meekdev.amnetic.client.model.internal.ModelRegistry.INSTANCE.closeAll();
            com.meekdev.amnetic.client.entityfx.internal.EntityEffectRegistry.INSTANCE.clear();
            com.meekdev.amnetic.client.entityfx.internal.SceneColorSnapshot.INSTANCE.dispose();
            com.meekdev.amnetic.client.geometry.EntityMeshTap.clear();
            com.meekdev.amnetic.client.entityfx.EntityTextureOverride.clearAll();
        });

        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(ctx -> {
            if (com.meekdev.amnetic.client.scene.internal.CaptureManager.INSTANCE.isCapturing()) return;
            boolean captured = ParticleSimulation.INSTANCE.captureSceneDepth();
            MainTargetFramebuffer.setDepthOverride(captured ? SceneDepth.snapshotDepthGlId() : 0);
            // Snapshot the world (opaque pass; before translucents) for surface effects to refract,
            // and push each active effect's live uniforms to the GPU before the body draws.
            if (!com.meekdev.amnetic.client.entityfx.internal.EntityEffectRegistry.INSTANCE.isEmpty()) {
                com.meekdev.amnetic.client.entityfx.internal.SceneColorSnapshot.INSTANCE.capture();
                com.meekdev.amnetic.client.entityfx.internal.EntityEffectRegistry.INSTANCE.uploadAll();
            }
        });

        LevelRenderEvents.END_MAIN.register(ctx -> {
            // These post-world passes must not run nested inside a scene capture's renderLevel — doing so
            // corrupts GL state and darkens the frame. The capture only wants vanilla terrain/entities.
            if (com.meekdev.amnetic.client.scene.internal.CaptureManager.INSTANCE.isCapturing()) return;
            Animations.update();
            InstanceMeshRegistry.INSTANCE.renderAll(InstancePhase.WORLD_LAST, ctx);
            // Models draw into the main target (color + depth) just before the deferred-lighting pass,
            // so they are lit by both the Minecraft lightmap and the custom Amnetic lights.
            com.meekdev.amnetic.client.model.internal.ModelRegistry.INSTANCE.flush(ctx);
            MainTargetFramebuffer.setDepthOverride(0);
            com.meekdev.amnetic.client.light.internal.DeferredLightingPass.INSTANCE.render();
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
