package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.post.internal.CameraState;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class CameraStateMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void amnetic$captureCameraState(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                            boolean renderBlockOutline, CameraRenderState camera,
                                            Matrix4fc viewRotation, GpuBufferSlice projection,
                                            Vector4f clippingPlanes, boolean sky,
                                            ChunkSectionsToRender sections, CallbackInfo ci) {
        Vec3 pos = camera.pos;
        CameraState.update(camera.projectionMatrix, camera.viewRotationMatrix, pos.x, pos.y, pos.z, camera.depthFar);
    }
}
