package com.meekdev.amnetic.client;

import com.meekdev.amnetic.client.compute.ComputeCapabilities;
import com.meekdev.amnetic.client.compute.ComputeSelfTest;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.particle.ParticleSimulation;
import com.meekdev.amnetic.client.particle.Particles;
import com.meekdev.amnetic.client.particle.SceneDepth;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import net.fabricmc.api.ClientModInitializer;
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
            ComputeSelfTest.runOnce();
        });

        Particles.init();

        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(ctx -> {
            boolean captured = ParticleSimulation.INSTANCE.captureSceneDepth();
            MainTargetFramebuffer.setDepthOverride(captured ? SceneDepth.snapshotDepthGlId() : 0);
        });

        LevelRenderEvents.END_MAIN.register(ctx -> {
            InstanceMeshRegistry.INSTANCE.renderAll(InstancePhase.WORLD_LAST, ctx);
            MainTargetFramebuffer.setDepthOverride(0);
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
