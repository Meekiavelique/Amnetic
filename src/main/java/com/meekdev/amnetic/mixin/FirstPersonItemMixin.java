package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.model.HandModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonItemMixin {
    @Redirect(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void amnetic$captureItem(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector,
                                    int light, int overlay, int outline,
                                    PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands,
                                    float partialTick, float pitch, InteractionHand hand, float swing,
                                    ItemStack stack, float height, PoseStack callerPose,
                                    SubmitNodeCollector callerCollector, int callerLight) {
        var entity = Minecraft.getInstance().player;
        boolean right = entity != null && entity.getMainArm() == HumanoidArm.RIGHT;
        if (hand == InteractionHand.OFF_HAND) right = !right;
        ItemDisplayContext context = right
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        if (!HandModels.captureHeld(entity, stack, context, pose)) {
            item.submit(pose, collector, light, overlay, outline);
        }
    }
}
