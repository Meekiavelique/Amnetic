package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.post.RenderPhase;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final private CrossFrameResourcePool resourcePool;

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void amnetic$skipHandInCapture(CallbackInfo ci) {
        if (CaptureManager.INSTANCE.isCapturing()) ci.cancel();
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
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void amnetic$onPostRender(DeltaTracker ticker, boolean renderLevel, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.POST_RENDER)) {
            PostEffectRegistry.INSTANCE.restorePostRenderDepthSnapshotInto(mc.getMainRenderTarget());
        }
        PostEffectRegistry.INSTANCE.applyAll(RenderPhase.POST_RENDER, ticker.getGameTimeDeltaPartialTick(true), resourcePool);
    }
}
