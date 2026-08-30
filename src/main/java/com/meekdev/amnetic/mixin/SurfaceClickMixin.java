package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.surface.internal.WorldSurfaceRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class SurfaceClickMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void amnetic$surfaceAttack(CallbackInfoReturnable<Boolean> cir) {
        if (WorldSurfaceRenderer.INSTANCE.isPointerOverSurface()) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void amnetic$surfaceContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (leftClick && WorldSurfaceRenderer.INSTANCE.isPointerOverSurface()) ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void amnetic$surfaceUse(CallbackInfo ci) {
        if (WorldSurfaceRenderer.INSTANCE.isPointerOverSurface()) ci.cancel();
    }
}
