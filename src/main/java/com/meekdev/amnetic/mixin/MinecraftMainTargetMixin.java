package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.pipeline.internal.LayerRedirect;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMainTargetMixin {

    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void amnetic$redirectCaptureTarget(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget capture = CaptureManager.INSTANCE.currentCaptureTarget();
        if (capture != null) {
            cir.setReturnValue(capture);
            return;
        }
        // while a layer (hand/GUI) is being isolated, vanilla draws into the layer target instead of the screen
        RenderTarget layer = LayerRedirect.active();
        if (layer != null) {
            cir.setReturnValue(layer);
        }
    }
}
