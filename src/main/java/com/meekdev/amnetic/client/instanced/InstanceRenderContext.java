package com.meekdev.amnetic.client.instanced;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;

public abstract class InstanceRenderContext {

    public abstract MinecraftClient client();

    public abstract ClientWorld world();

    public abstract float deltaTick();

    public abstract Vec3d cameraPos();

    public abstract Matrix4fc viewMatrix();

    public abstract Matrix4fc projectionMatrix();

    public Matrix4f worldToModel(Vec3d pos) {
        Vec3d cam = cameraPos();
        return new Matrix4f().translation(
                (float)(pos.x - cam.x),
                (float)(pos.y - cam.y),
                (float)(pos.z - cam.z)
        );
    }

    public Matrix4f worldToModel(double x, double y, double z) {
        Vec3d cam = cameraPos();
        return new Matrix4f().translation(
                (float)(x - cam.x),
                (float)(y - cam.y),
                (float)(z - cam.z)
        );
    }

    public Matrix4f worldToModel(BlockPos pos) {
        return worldToModel(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public Matrix4f worldToModel(Vec3d pos, float scale) {
        return worldToModel(pos).scale(scale);
    }

    public Matrix4f worldToModel(Vec3d pos, float yawDegrees, float scale) {
        return worldToModel(pos)
                .rotateY((float) Math.toRadians(yawDegrees))
                .scale(scale);
    }

    public Matrix4f worldToModel(Vec3d pos, Quaternionfc rotation, float scale) {
        return worldToModel(pos)
                .rotate(rotation)
                .scale(scale);
    }

    public Matrix4f worldToModel(Entity entity) {
        return worldToModel(entity.getLerpedPos(deltaTick()));
    }

    public Matrix4f worldToModel(Entity entity, float scale) {
        return worldToModel(entity.getLerpedPos(deltaTick()), scale);
    }

    public Matrix4f worldToModel(Entity entity, float yawDegrees, float scale) {
        return worldToModel(entity.getLerpedPos(deltaTick()), yawDegrees, scale);
    }
}
