package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.entityfx.EntityEffect;
import com.meekdev.amnetic.client.entityfx.internal.SurfaceTarget;
import com.meekdev.amnetic.client.geometry.PosedMesh;
import com.meekdev.amnetic.client.geometry.internal.MeshTapTarget;
import com.meekdev.amnetic.client.entityfx.internal.TextureOverrideTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Shadow protected EntityModel model;

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
}
