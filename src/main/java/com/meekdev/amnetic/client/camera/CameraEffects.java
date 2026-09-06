package com.meekdev.amnetic.client.camera;

import com.meekdev.amnetic.client.anim.Easing;
import com.meekdev.amnetic.client.camera.effect.Kick;
import com.meekdev.amnetic.client.camera.effect.Shake;
import com.meekdev.amnetic.client.camera.effect.Spring;
import com.meekdev.amnetic.client.camera.internal.CameraController;
import net.minecraft.world.phys.Vec3;

// shake, kick and spring are ordinary CameraModifiers, nothing here reaches past the same
// api your own modifier would use
public final class CameraEffects {

    private static Shake shake;
    private static Spring spring;

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
        addModifier(new Kick(positionDelta, pitch, yaw, roll, fov, durationSeconds, easing));
    }

    public static void kick(float pitch, float yaw, float roll, float durationSeconds) {
        impulse(Vec3.ZERO, pitch, yaw, roll, 0f, durationSeconds, Easing.EASE_OUT);
    }

    public static void fovPunch(float fovDegrees, float durationSeconds) {
        impulse(Vec3.ZERO, 0f, 0f, 0f, fovDegrees, durationSeconds, Easing.EASE_OUT);
    }

    public static void shake(float trauma) {
        shake().add(trauma);
    }

    public static void shake(float trauma, float positionScale, float rotationScale) {
        shake().scale(positionScale, rotationScale).add(trauma);
    }

    public static void shakeDecay(float perSecond) {
        shake().decay(perSecond);
    }

    public static void springTo(Vec3 targetOffset, float stiffness, float damping) {
        spring().to(targetOffset, stiffness, damping);
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
        shake = null;
        spring = null;
        c().clear();
    }

    // the shared instances drop out of the list when they settle, re-adding rather than
    // remaking them is what keeps a shakeDecay or a stiffness set earlier
    private static Shake shake() {
        if (shake == null) shake = new Shake();
        addModifier(shake);
        return shake;
    }

    private static Spring spring() {
        if (spring == null) spring = new Spring();
        addModifier(spring);
        return spring;
    }
}
