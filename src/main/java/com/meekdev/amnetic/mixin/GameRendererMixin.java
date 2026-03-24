package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.post.RenderPhase;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Pool;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final private Pool pool;

    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void amnetic$onPostWorldRender(RenderTickCounter ticker, CallbackInfo ci) {
        PostEffectRegistry.INSTANCE.applyAll(RenderPhase.POST_WORLD, ticker.getTickProgress(true), pool);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void amnetic$onPostRender(RenderTickCounter ticker, boolean renderLevel, CallbackInfo ci) {
        PostEffectRegistry.INSTANCE.applyAll(RenderPhase.POST_RENDER, ticker.getTickProgress(true), pool);
    }
}
