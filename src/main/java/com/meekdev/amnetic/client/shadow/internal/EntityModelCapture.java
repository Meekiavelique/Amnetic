package com.meekdev.amnetic.client.shadow.internal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//?} else {
/*import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
*///?}

public final class EntityModelCapture {

    private EntityModelCapture() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean capture(Entity entity, float partialTick, double anchorX, double anchorY, double anchorZ,
                                  CaptureConsumer consumer) {
        try {
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            //? if >=1.21.2 {
            EntityRenderState state = dispatcher.extractEntity(entity, partialTick);
            if (!(state instanceof LivingEntityRenderState lrs)) return false;
            //?} else {
            /*if (!(entity instanceof LivingEntity living)) return false;
            *///?}

            var renderer = dispatcher.getRenderer(entity);
            if (!(renderer instanceof LivingEntityRenderer livingRenderer)) return false;
            EntityModel model = livingRenderer.getModel();
            if (model == null) return false;

            //? if >=1.21.2 {
            float bodyRot = lrs.bodyRot;
            float scale = lrs.scale;
            //?} else {
            /*float bodyRot = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
            float scale = living.getScale();
            *///?}

            Vec3 p = entity.getPosition(partialTick);
            PoseStack pose = new PoseStack();
            pose.translate(p.x - anchorX, p.y - anchorY, p.z - anchorZ);
            pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyRot));
            float s = scale <= 0f ? 1f : scale;
            pose.scale(-s, -s, s);
            pose.translate(0.0, -1.501, 0.0);

            //? if >=1.21.2 {
            model.setupAnim(lrs);
            //?} else {
            /*float headRot = Mth.rotLerp(partialTick, living.yHeadRotO, living.yHeadRot);
            float pitch = Mth.lerp(partialTick, living.xRotO, living.getXRot());
            float limbSpeed = 0f;
            float limbPos = 0f;
            if (!living.isPassenger() && living.isAlive()) {
                limbSpeed = Math.min(1f, living.walkAnimation.speed(partialTick));
                limbPos = living.walkAnimation.position(partialTick) * (living.isBaby() ? 3f : 1f);
            }
            model.prepareMobModel(living, limbPos, limbSpeed, partialTick);
            model.setupAnim(living, limbPos, limbSpeed, living.tickCount + partialTick,
                    Mth.wrapDegrees(headRot - bodyRot), pitch);
            *///?}
            //? if >=1.21 {
            model.renderToBuffer(pose, consumer, 15728880, OverlayTexture.NO_OVERLAY);
            //?} else {
            /*model.renderToBuffer(pose, consumer, 15728880, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            *///?}
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
