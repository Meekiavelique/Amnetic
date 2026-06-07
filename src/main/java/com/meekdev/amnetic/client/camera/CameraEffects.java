package com.meekdev.amnetic.client.camera;

import com.meekdev.amnetic.client.camera.internal.CameraController;
import com.meekdev.amnetic.client.particle.Easing;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class CameraEffects {

    private CameraEffects() {}

    private static CameraController c() {
        return CameraController.INSTANCE;
    }

    public static void addPositionOffset(Vec3 offset) {
        c().addPositionOffset(offset.x, offset.y, offset.z);
    }

    public static void addPositionOffset(double x, double y, double z) {
        c().addPositionOffset(x, y, z);
    }

    public static void addRotationOffset(float pitch, float yaw, float roll) {
        c().addRotationOffset(pitch, yaw, roll);
    }

    public static void addFovOffset(float fovDegrees) {
        c().addFovOffset(fovDegrees);
    }

    public static void impulse(Vec3 positionDelta, float pitch, float yaw, float roll, float fov,
                               float durationSeconds, Easing easing) {
        c().impulse(new Vector3f((float) positionDelta.x, (float) positionDelta.y, (float) positionDelta.z),
                pitch, yaw, roll, fov, durationSeconds, easing);
    }

    public static void kick(float pitch, float yaw, float roll, float durationSeconds) {
        impulse(Vec3.ZERO, pitch, yaw, roll, 0f, durationSeconds, Easing.EASE_OUT);
    }

    public static void fovPunch(float fovDegrees, float durationSeconds) {
        impulse(Vec3.ZERO, 0f, 0f, 0f, fovDegrees, durationSeconds, Easing.EASE_OUT);
    }

    public static void shake(float trauma) {
        c().shake(trauma);
    }

    public static void shake(float trauma, float positionScale, float rotationScale) {
        c().shake(trauma, positionScale, rotationScale);
    }

    public static void shakeDecay(float perSecond) {
        c().shakeDecay(perSecond);
    }

    public static void springTo(Vec3 targetOffset, float stiffness, float damping) {
        c().springTo(targetOffset.x, targetOffset.y, targetOffset.z, stiffness, damping);
    }

    public static void addModifier(CameraModifier modifier) {
        c().addModifier(modifier);
    }

    public static void removeModifier(CameraModifier modifier) {
        c().removeModifier(modifier);
    }

    public static void clearModifiers() {
        c().clearModifiers();
    }

    public static void clear() {
        c().clear();
    }
}
