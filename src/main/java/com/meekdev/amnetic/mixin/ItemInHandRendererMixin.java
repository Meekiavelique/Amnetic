package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.model.HandModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    //? if >=1.21.9 {
    private void amnetic$captureHeldItem(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx,
                                         PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
    //?} else {
    /*private void amnetic$captureHeldItem(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, boolean leftHand,
                                         PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
    *///?}
        if (HandModels.captureHeld(entity, stack, ctx, pose)) {
            ci.cancel();
        }
    }
}
