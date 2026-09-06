package com.meekdev.amnetic.client.camera.effect;

import com.meekdev.amnetic.client.anim.Easing;
import com.meekdev.amnetic.client.anim.EasingFunction;
import com.meekdev.amnetic.client.anim.Interpolators;
import com.meekdev.amnetic.client.anim.Tween;
import com.meekdev.amnetic.client.camera.CameraFrame;
import com.meekdev.amnetic.client.camera.CameraModifier;
import net.minecraft.world.phys.Vec3;

public final class Kick implements CameraModifier {

    private final Vec3 position;
    private final float pitch;
    private final float yaw;
    private final float roll;
    private final float fov;
    private final EasingFunction easing;
    private final Tween<Float> clock;

    public Kick(Vec3 position, float pitch, float yaw, float roll, float fov,
                float durationSeconds, Easing easing) {
        this.position = position == null ? Vec3.ZERO : position;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.fov = fov;
        this.easing = easing != null ? easing : Easing.EASE_OUT;
        this.clock = new Tween<>(0f, 1f, Math.max(1.0e-3f, durationSeconds), Interpolators.FLOAT);
    }

    @Override
    public void modify(CameraFrame frame) {
        clock.update(frame.dt());
        // full at the start, gone at the end
        float env = easing.apply(1f - clock.value());
        if (position.lengthSqr() > 0) {
            frame.addPosition(position.x * env, position.y * env, position.z * env);
        }
        if (pitch != 0f || yaw != 0f || roll != 0f) frame.addRotation(pitch * env, yaw * env, roll * env);
        if (fov != 0f) frame.addFov(fov * env);
    }

    @Override
    public boolean finished() {
        return clock.isDone();
    }
}
