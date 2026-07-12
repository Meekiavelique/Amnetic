package com.meekdev.amnetic.client.shadow.internal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class EntityModelCapture {

    private EntityModelCapture() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean capture(Entity entity, float partialTick, double anchorX, double anchorY, double anchorZ,
                                  CaptureConsumer consumer) {
        try {
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            EntityRenderState state = dispatcher.extractEntity(entity, partialTick);
            if (!(state instanceof LivingEntityRenderState lrs)) return false;

            EntityRenderer<?, ?> renderer = dispatcher.getRenderer(entity);
            if (!(renderer instanceof LivingEntityRenderer living)) return false;
            EntityModel model = living.getModel();
            if (model == null) return false;

            Vec3 p = entity.getPosition(partialTick);
            PoseStack pose = new PoseStack();
            pose.translate(p.x - anchorX, p.y - anchorY, p.z - anchorZ);
            pose.mulPose(Axis.YP.rotationDegrees(180.0f - lrs.bodyRot));
            float s = lrs.scale <= 0f ? 1f : lrs.scale;
            pose.scale(-s, -s, s);
            pose.translate(0.0, -1.501, 0.0);

            model.setupAnim(lrs);
            model.renderToBuffer(pose, consumer, 15728880, OverlayTexture.NO_OVERLAY);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
