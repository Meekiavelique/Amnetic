package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.entityfx.EntityEffect;
import com.meekdev.amnetic.client.geometry.PosedMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=26.1 {
import com.meekdev.amnetic.client.entityfx.internal.SurfaceTarget;
import com.meekdev.amnetic.client.entityfx.internal.TextureOverrideTarget;
import com.meekdev.amnetic.client.geometry.internal.MeshTapTarget;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
//?} else if >=1.21.2 {
/*import com.meekdev.amnetic.client.entityfx.internal.SurfaceTarget;
import com.meekdev.amnetic.client.entityfx.internal.TextureOverrideTarget;
import com.meekdev.amnetic.client.geometry.internal.MeshTapTarget;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
*///?} else {
/*import com.meekdev.amnetic.client.entityfx.internal.EntityEffectRegistry;
import com.meekdev.amnetic.client.entityfx.internal.TextureOverrideRegistry;
import com.meekdev.amnetic.client.geometry.internal.MeshTapRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
*///?}


@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Shadow protected EntityModel model;

    //? if >=1.21.2 {
    @Shadow public abstract Identifier getTextureLocation(LivingEntityRenderState state);

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void amnetic$swapRenderType(LivingEntityRenderState state, boolean isBodyVisible, boolean forceTransparent,
                                        boolean appearsGlowing, CallbackInfoReturnable<RenderType> cir) {
        if (state instanceof TextureOverrideTarget ov && ov.amnetic$getTextureOverride() != null) {
            cir.setReturnValue(RenderTypes.entityCutout(ov.amnetic$getTextureOverride()));
            return;
        }
        if (state instanceof SurfaceTarget target) {
            EntityEffect effect = target.amnetic$getSurfaceEffect();
            if (effect != null && !effect.isRemoved() && effect.replacesBody()) {
                cir.setReturnValue(effect.renderType(getTextureLocation(state)));
            }
        }
    }

    @Inject(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private void amnetic$captureMesh(LivingEntityRenderState state, PoseStack poseStack,
                                     SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof MeshTapTarget target)) return;
        PosedMesh tap = target.amnetic$getMeshTap();
        if (tap != null && !tap.isRemoved()) {
            tap.captureFrom(model, state, poseStack);
        }
    }

    @Inject(method = "shouldRenderLayers", at = @At("HEAD"), cancellable = true)
    private void amnetic$hideLayers(LivingEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
        if (state instanceof TextureOverrideTarget ov && ov.amnetic$getTextureOverride() != null
                && ov.amnetic$isOverrideHideLayers()) {
            cir.setReturnValue(false);   // hide armor/elytra/held items
            return;
        }
        if (state instanceof SurfaceTarget target) {
            EntityEffect effect = target.amnetic$getSurfaceEffect();
            if (effect != null && !effect.isRemoved() && effect.replacesBody()) {
                cir.setReturnValue(false);
            }
        }
    }
    //?} else {
    /*@Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void amnetic$swapRenderType(LivingEntity entity, boolean isBodyVisible, boolean forceTransparent,
                                        boolean appearsGlowing, CallbackInfoReturnable<RenderType> cir) {
        if (TextureOverrideRegistry.INSTANCE.isEmpty()) return;
        TextureOverrideRegistry.Entry override = TextureOverrideRegistry.INSTANCE.get(entity);
        if (override != null) cir.setReturnValue(RenderType.entityCutout(override.texture()));
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void amnetic$captureMesh(LivingEntity entity, float yaw, float partialTick, PoseStack poseStack,
                                     MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (MeshTapRegistry.INSTANCE.isEmpty()) return;
        PosedMesh tap = MeshTapRegistry.INSTANCE.get(entity);
        if (tap != null && !tap.isRemoved()) {
            tap.captureFrom(model, poseStack);
        }
    }

    @org.spongepowered.asm.mixin.injection.Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSpectator()Z"))
    private boolean amnetic$hideLayers(LivingEntity entity) {
        if (!TextureOverrideRegistry.INSTANCE.isEmpty()) {
            TextureOverrideRegistry.Entry override = TextureOverrideRegistry.INSTANCE.get(entity);
            if (override != null && override.hideLayers()) return true;
        }
        return entity.isSpectator();
    }
    *///?}
}
