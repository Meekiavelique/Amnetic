package com.meekdev.amnetic.client.instanced;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;

public abstract class InstanceRenderContext {

    public abstract Minecraft client();

    public abstract ClientLevel world();

    public abstract float deltaTick();

    public abstract Vec3 cameraPos();

    public abstract Matrix4fc viewMatrix();

    public abstract Matrix4fc projectionMatrix();

    public float gameTime() {
        ClientLevel level = world();
        if (level == null) return 0f;
        return (level.getGameTime() % 24000L + deltaTick()) / 24000.0f;
    }

    public Matrix4f worldToModel(Vec3 pos) {
        Vec3 cam = cameraPos();
        return new Matrix4f().translation(
                (float)(pos.x - cam.x),
                (float)(pos.y - cam.y),
                (float)(pos.z - cam.z)
        );
    }

    public Matrix4f worldToModel(double x, double y, double z) {
        Vec3 cam = cameraPos();
        return new Matrix4f().translation(
                (float)(x - cam.x),
                (float)(y - cam.y),
                (float)(z - cam.z)
        );
    }

    public Matrix4f worldToModel(BlockPos pos) {
        return worldToModel(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public Matrix4f worldToModel(Vec3 pos, float scale) {
        return worldToModel(pos).scale(scale);
    }

    public Matrix4f worldToModel(Vec3 pos, float yawDegrees, float scale) {
        return worldToModel(pos)
                .rotateY((float) Math.toRadians(yawDegrees))
                .scale(scale);
    }

    public Matrix4f worldToModel(Vec3 pos, Quaternionfc rotation, float scale) {
        return worldToModel(pos)
                .rotate(rotation)
                .scale(scale);
    }

    public Matrix4f worldToModel(Entity entity) {
        return worldToModel(entity.getPosition(deltaTick()));
    }

    public Matrix4f worldToModel(Entity entity, float scale) {
        return worldToModel(entity.getPosition(deltaTick()), scale);
    }

    public Matrix4f worldToModel(Entity entity, float yawDegrees, float scale) {
        return worldToModel(entity.getPosition(deltaTick()), yawDegrees, scale);
    }
}
