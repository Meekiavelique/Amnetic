package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

final class MinecraftRenderContext extends InstanceRenderContext {

    private final Minecraft client;
    private final float deltaTick;
    private final Matrix4f viewMatrix;
    private final Matrix4f projectionMatrix;

    private Vec3 cameraPos;

    MinecraftRenderContext(Minecraft client, float deltaTick, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        this.client = client;
        this.deltaTick = deltaTick;
        this.viewMatrix = viewMatrix;
        this.projectionMatrix = projectionMatrix;
    }

    @Override
    public Minecraft client() { return client; }

    @Override
    public ClientLevel world() { return client.level; }

    @Override
    public float deltaTick() { return deltaTick; }

    @Override
    public Vec3 cameraPos() {
        if (cameraPos == null) {
            cameraPos = client.gameRenderer.getMainCamera().position();
        }
        return cameraPos;
    }

    @Override
    public Matrix4fc viewMatrix() {
        return viewMatrix;
    }

    @Override
    public Matrix4fc projectionMatrix() {
        return projectionMatrix;
    }
}
