package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.amnetic.client.instanced.internal.MinecraftFramebufferUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

@Mixin(GameRenderer.class)
public abstract class WorldLastInstancingMixin {

    @Shadow @Final private MinecraftClient client;

    @Redirect(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"
            )
    )
    private void amnetic$renderWorldAndWorldLast(
            WorldRenderer worldRenderer,
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f viewRotationMatrix,
            Matrix4f worldProjectionMatrix,
            Matrix4f frustumProjectionMatrix,
            GpuBufferSlice projectionBufferSlice,
            Vector4f clippingPlanes,
            boolean someFlag
    ) {
        Matrix4f view = new Matrix4f(viewRotationMatrix);
        view.m30(0);
        view.m31(0);
        view.m32(0);
        Matrix4f projection = new Matrix4f(worldProjectionMatrix);

        float deltaTick = tickCounter.getTickProgress(true);

        worldRenderer.render(
                allocator,
                tickCounter,
                renderBlockOutline,
                camera,
                viewRotationMatrix,
                worldProjectionMatrix,
                frustumProjectionMatrix,
                projectionBufferSlice,
                clippingPlanes,
                someFlag
        );

        if (client.world == null || client.player == null) return;

        MinecraftFramebufferUtil.SavedState saved = MinecraftFramebufferUtil.bindMainFramebuffer(client, true);
        try {
            InstanceMeshRegistry.INSTANCE.renderAll(InstancePhase.WORLD_LAST, client, deltaTick, view, projection);
        } finally {
            MinecraftFramebufferUtil.restore(saved);
        }
    }
}
