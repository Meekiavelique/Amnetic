package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.camera.internal.FrameView;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderLayer;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.model.HandModels;
import com.meekdev.amnetic.client.model.ViewModels;
import com.meekdev.amnetic.client.pipeline.internal.LayerSystem;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import com.meekdev.amnetic.client.render.AfterWorldRender;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import com.meekdev.amnetic.client.taa.Taa;
import com.meekdev.amnetic.client.taa.internal.TaaJitter;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final private CrossFrameResourcePool resourcePool;

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void amnetic$skipHandInCapture(CameraRenderState cam, float partialTick, Matrix4fc handProjection, CallbackInfo ci) {
        if (CaptureManager.INSTANCE.isCapturing()) {
            ci.cancel();
            return;
        }
        HandModels.captureProjection(handProjection);
        HandModels.beginFrame();
        ViewModels.captureProjection(handProjection);
        LayerSystem.INSTANCE.begin(RenderLayer.HAND);
    }

    @Inject(method = "renderItemInHand", at = @At("TAIL"))
    private void amnetic$endHandLayer(CallbackInfo ci) {
        // draw the custom held model now, same frame as its captured pose, so it tracks view bob 1:1
        HandModels.render();
        // view-space models draw right after the hand, sharing the same FOV/frame
        ViewModels.render();
        LayerSystem.INSTANCE.end(RenderLayer.HAND);
        // here and not at the OVERLAY injection, that one fires before the hand draws
        Pipeline.runStage(RenderStage.AFTER_HAND);
    }

    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;",
                    ordinal = 0
            )
    )
    private GpuBufferSlice amnetic$captureBobbedProjection(ProjectionMatrixBuffer buffer, Matrix4f projectionMatrix) {
        if (!CaptureManager.INSTANCE.isCapturing()) {
            if (Taa.jitterActive()) {
                var target = Minecraft.getInstance().getMainRenderTarget();
                TaaJitter.INSTANCE.beginFrame(target.width, target.height);
                TaaJitter.INSTANCE.applyTo(projectionMatrix);
            } else {
                TaaJitter.INSTANCE.reset();
            }
            FrameView.INSTANCE.setProjection(projectionMatrix);
        }
        return buffer.getBuffer(projectionMatrix);
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void amnetic$skipOutlineInCapture(CallbackInfoReturnable<Boolean> cir) {
        if (CaptureManager.INSTANCE.isCapturing()) cir.setReturnValue(false);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void amnetic$sceneCapture(DeltaTracker ticker, boolean renderLevel, CallbackInfo ci) {
        if (renderLevel) CaptureManager.INSTANCE.runCaptures((GameRenderer) (Object) this, ticker);
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void amnetic$onPostWorldRender(DeltaTracker ticker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.PRE_GUI)
                || PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.POST_RENDER)) {
            PostEffectRegistry.INSTANCE.captureWorldDepthSnapshot(mc.getMainRenderTarget());
        }
        PostEffectRegistry.INSTANCE.applyAll(RenderPhase.POST_WORLD, ticker.getGameTimeDeltaPartialTick(true), resourcePool);
        AfterWorldRender.fire();
        Pipeline.runStage(RenderStage.OVERLAY);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void amnetic$onPreScreenDepthClear(DeltaTracker ticker, boolean renderLevel, CallbackInfo ci) {
        if (!renderLevel) return;

        Minecraft mc = Minecraft.getInstance();
        if (PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.POST_RENDER)) {
            PostEffectRegistry.INSTANCE.capturePostRenderDepthSnapshot(mc.getMainRenderTarget());
        }
        PostEffectRegistry.INSTANCE.applyAll(RenderPhase.PRE_GUI, ticker.getGameTimeDeltaPartialTick(true), resourcePool);
        Pipeline.runStage(RenderStage.BEFORE_GUI);
        LayerSystem.INSTANCE.begin(RenderLayer.GUI);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void amnetic$onPostRender(DeltaTracker ticker, boolean renderLevel, CallbackInfo ci) {
        LayerSystem.INSTANCE.end(RenderLayer.GUI);
        Minecraft mc = Minecraft.getInstance();
        if (PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.POST_RENDER)) {
            PostEffectRegistry.INSTANCE.restorePostRenderDepthSnapshotInto(mc.getMainRenderTarget());
        }
        PostEffectRegistry.INSTANCE.applyAll(RenderPhase.POST_RENDER, ticker.getGameTimeDeltaPartialTick(true), resourcePool);
        Pipeline.runStage(RenderStage.AFTER_GUI);
    }
}
