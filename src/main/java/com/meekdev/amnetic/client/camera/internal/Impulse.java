package com.meekdev.amnetic.client.camera.internal;

import com.meekdev.amnetic.client.particle.Easing;
import org.joml.Vector3f;

public final class Impulse {

    private final Vector3f posDelta;   // world-space blocks
    private final float pitch, yaw, roll; // degrees
    private final float fov;            // degrees
    private final float duration;      // seconds
    private final Easing easing;
    private float elapsed;

    public Impulse(Vector3f posDelta, float pitch, float yaw, float roll, float fov,
                   float duration, Easing easing) {
        this.posDelta = posDelta;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.fov = fov;
        this.duration = Math.max(1.0e-3f, duration);
        this.easing = easing != null ? easing : Easing.EASE_OUT;
    }

    public void advance(float dt) {
        elapsed += dt;
    }

    public boolean dead() {
        return elapsed >= duration;
    }

    void accumulate(CameraController c) {
        float t = Math.min(1f, elapsed / duration);
        float env = easing.apply(1f - t); // full at t=0, zero at t=1
        c.addWorld(posDelta.x * env, posDelta.y * env, posDelta.z * env);
        c.addRotation(pitch * env, yaw * env, roll * env);
        c.addFov(fov * env);
    }
}
