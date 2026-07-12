package com.meekdev.amnetic.client.camera;

import com.meekdev.amnetic.client.camera.internal.CameraController;
import net.minecraft.world.phys.Vec3;

public final class CameraFrame {
    private final CameraController owner;

    private float dt;
    private float time;
    private Vec3 cameraPos = Vec3.ZERO;
    private float yaw;
    private float pitch;
    private float fov;

    public CameraFrame(CameraController owner) {
        this.owner = owner;
    }

    public void begin(float dt, float time, Vec3 cameraPos, float yaw, float pitch, float fov) {
        this.dt = dt;
        this.time = time;
        this.cameraPos = cameraPos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fov = fov;
    }

    public float dt() {
        return dt;
    }

    public float time() {
        return time;
    }

    public Vec3 cameraPosition() {
        return cameraPos;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float fov() {
        return fov;
    }

    public void addPosition(Vec3 offset) {
        owner.contributeWorld(offset.x, offset.y, offset.z);
    }

    public void addPosition(double x, double y, double z) {
        owner.contributeWorld(x, y, z);
    }

    public void addPositionLocal(double right, double up, double forward) {
        owner.contributeLocal(right, up, forward);
    }

    public void addRotation(float pitch, float yaw, float roll) {
        owner.contributeRotation(pitch, yaw, roll);
    }

    public void addFov(float fov) {
        owner.contributeFov(fov);
    }
}
