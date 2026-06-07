package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.post.RenderPhase;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
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

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final private CrossFrameResourcePool resourcePool;

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
