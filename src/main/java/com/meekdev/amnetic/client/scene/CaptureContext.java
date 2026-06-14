package com.meekdev.amnetic.client.scene;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class CaptureContext {

    private Camera mainCamera;
    private float partialTicks;
    private final Matrix4f mainViewRotation = new Matrix4f();
    private final Matrix4f mainProjection = new Matrix4f();
    private Vec3 mainEye = Vec3.ZERO;

    public void set(Camera cam, float partialTicks, Matrix4f mainViewRotation, Matrix4f mainProjection, Vec3 mainEye) {
        this.mainCamera = cam;
        this.partialTicks = partialTicks;
        this.mainViewRotation.set(mainViewRotation);
        this.mainProjection.set(mainProjection);
        this.mainEye = mainEye;
    }

    public Camera mainCamera() { return mainCamera; }

    public float partialTicks() { return partialTicks; }

    public Vec3 mainEye() { return mainEye; }

    public Matrix4f mainViewRotation() { return mainViewRotation; }

    public Matrix4f mainProjection() { return mainProjection; }
}
