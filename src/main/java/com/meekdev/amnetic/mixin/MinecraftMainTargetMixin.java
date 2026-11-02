package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.pipeline.internal.LayerRedirect;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
//? if >=26.2 {
/*import net.minecraft.client.renderer.GameRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.Redirect;
*///?} else {
import net.minecraft.client.Minecraft;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >=26.2 {
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
//?} else {
/*@Mixin(Minecraft.class)
*///?}
public abstract class MinecraftMainTargetMixin {

    //? if >=26.2 {
    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    *///?}
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

    //? if >=26.2 {
    /*// renderLevel reads the field directly now, send it through the getter like older versions did
    @Redirect(method = "renderLevel", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget amnetic$levelMainTarget(GameRenderer renderer) {
        return renderer.mainRenderTarget();
    }
    *///?}
}
