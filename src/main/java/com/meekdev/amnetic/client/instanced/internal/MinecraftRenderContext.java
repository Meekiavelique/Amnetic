package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

final class MinecraftRenderContext extends InstanceRenderContext {

    private final MinecraftClient client;
    private final float deltaTick;
    private final Matrix4f viewMatrix;
    private final Matrix4f projectionMatrix;

    private Vec3d cameraPos;

    MinecraftRenderContext(MinecraftClient client, float deltaTick, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        this.client = client;
        this.deltaTick = deltaTick;
        this.viewMatrix = viewMatrix;
        this.projectionMatrix = projectionMatrix;
    }

    @Override
    public MinecraftClient client() { return client; }

    @Override
    public ClientWorld world() { return client.world; }

    @Override
    public float deltaTick() { return deltaTick; }

    @Override
    public Vec3d cameraPos() {
        if (cameraPos == null) {
            cameraPos = client.gameRenderer.getCamera().getCameraPos();
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
