package com.meekdev.amnetic.client.camera.internal;

import com.meekdev.amnetic.client.anim.Easing;
import com.meekdev.amnetic.client.anim.EasingFunction;
import com.meekdev.amnetic.client.anim.Interpolators;
import com.meekdev.amnetic.client.anim.Tween;
import org.joml.Vector3f;

public final class Impulse {

    private final Vector3f posDelta;   // world-space blocks
    private final float pitch, yaw, roll; // degrees
    private final float fov;            // degrees
    private final EasingFunction easing;
    private final Tween<Float> clock;   // 0 -> 1 over duration, linear

    public Impulse(Vector3f posDelta, float pitch, float yaw, float roll, float fov,
                   float duration, Easing easing) {
        this.posDelta = posDelta;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.fov = fov;
        this.easing = easing != null ? easing : Easing.EASE_OUT;
        this.clock = new Tween<>(0f, 1f, Math.max(1.0e-3f, duration), Interpolators.FLOAT);
    }

    public void advance(float dt) {
        clock.update(dt);
    }

    public boolean dead() {
        return clock.isDone();
    }

    void accumulate(CameraController c) {
        float env = easing.apply(1f - clock.value()); // full at t=0, zero at t=1
        c.addWorld(posDelta.x * env, posDelta.y * env, posDelta.z * env);
        c.addRotation(pitch * env, yaw * env, roll * env);
        c.addFov(fov * env);
    }
}
