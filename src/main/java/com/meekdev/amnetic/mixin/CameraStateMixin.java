package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.AmneticClient;
import com.meekdev.amnetic.client.camera.internal.FrameView;
import com.meekdev.amnetic.client.post.internal.CameraState;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
//?} else if >=1.21.5 {
/*import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.render.LevelCamera;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} else {
/*import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.render.LevelCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Shadow;
*///?}

@Mixin(LevelRenderer.class)
public abstract class CameraStateMixin {

    //? if >=26.1 {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void amnetic$captureCameraState(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                            boolean renderBlockOutline, CameraRenderState camera,
                                            Matrix4fc viewRotation, GpuBufferSlice projection,
                                            Vector4f clippingPlanes, boolean sky,
                                            ChunkSectionsToRender sections, CallbackInfo ci) {
        Vec3 pos = camera.pos;
        CameraState.update(camera.projectionMatrix, camera.viewRotationMatrix, pos.x, pos.y, pos.z, camera.depthFar);

        if (!CaptureManager.INSTANCE.isCapturing()) {
            FrameView.INSTANCE.set(viewRotation);
        }
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void amnetic$renderPost(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                    boolean renderBlockOutline, CameraRenderState camera,
                                    Matrix4fc viewRotation, GpuBufferSlice projection,
                                    Vector4f clippingPlanes, boolean sky,
                                    ChunkSectionsToRender sections, CallbackInfo ci) {
        AmneticClient.renderPost();
    }
    //?} else if >=1.21.5 {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    private void amnetic$captureCameraState(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                            boolean renderBlockOutline, Camera camera,
                                            Matrix4f viewRotation, Matrix4f projection, Matrix4f cullProjection,
                                            GpuBufferSlice fog, Vector4f fogColor, boolean sky, CallbackInfo ci) {
        Vec3 pos = camera.position();
        LevelCamera.record(pos, projection, viewRotation);
        CameraState.update(projection, viewRotation, pos.x, pos.y, pos.z,
                Minecraft.getInstance().gameRenderer.getDepthFar());

        if (!CaptureManager.INSTANCE.isCapturing()) {
            FrameView.INSTANCE.set(viewRotation);
        }
    }

    @Inject(method = "prepareCullFrustum", at = @At("RETURN"))
    private void amnetic$recordCullFrustum(Matrix4f viewRotation, Matrix4f projection, Vec3 pos,
                                           CallbackInfoReturnable<Frustum> cir) {
        VanillaCompat.recordCullFrustum(cir.getReturnValue());
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void amnetic$renderPost(CallbackInfo ci) {
        AmneticClient.renderPost();
    }
    *///?} else {
    /*@Shadow private Frustum cullingFrustum;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void amnetic$captureCameraState(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
                                            GameRenderer gameRenderer, LightTexture lightTexture,
                                            Matrix4f viewRotation, Matrix4f projection, CallbackInfo ci) {
        Vec3 pos = camera.getPosition();
        LevelCamera.record(pos, projection, viewRotation);
        CameraState.update(projection, viewRotation, pos.x, pos.y, pos.z, gameRenderer.getDepthFar());

        if (!CaptureManager.INSTANCE.isCapturing()) {
            FrameView.INSTANCE.set(viewRotation);
        }
    }

    @Inject(method = "prepareCullFrustum", at = @At("TAIL"))
    private void amnetic$recordCullFrustum(Vec3 pos, Matrix4f viewRotation, Matrix4f projection, CallbackInfo ci) {
        VanillaCompat.recordCullFrustum(cullingFrustum);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void amnetic$renderPost(CallbackInfo ci) {
        AmneticClient.renderPost();
    }
    *///?}
}
