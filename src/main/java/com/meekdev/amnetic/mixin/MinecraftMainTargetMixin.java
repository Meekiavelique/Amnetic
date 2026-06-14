package com.meekdev.amnetic.mixin;

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
        }
    }
}
