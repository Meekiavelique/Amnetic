package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.model.HandModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
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
    private void amnetic$captureHeldItem(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx,
                                         PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (HandModels.captureHeld(entity, stack, ctx, pose)) {
            ci.cancel();
        }
    }
}
