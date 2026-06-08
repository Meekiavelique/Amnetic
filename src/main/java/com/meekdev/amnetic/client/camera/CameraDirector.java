package com.meekdev.amnetic.client.camera;

import com.meekdev.amnetic.client.camera.internal.CameraController;
import com.meekdev.amnetic.client.anim.Easing;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class CameraDirector {

    private CameraDirector() {}

    private static CameraController c() {
        return CameraController.INSTANCE;
    }

    public record Keyframe(Vec3 position, Vec3 lookAt, float time) {}

    public static boolean isActive() {
        return c().directorActive();
    }

    public static void moveTo(Vec3 target, Vec3 lookAt, Float fov, float durationSeconds, Easing easing) {
        c().moveTo(target, lookAt, fov, durationSeconds, easing);
    }

    public static void orbit(Vec3 center, double radius, float speedDegreesPerSecond) {
        c().orbit(center, radius, speedDegreesPerSecond);
    }

    public static void followPath(List<Keyframe> keyframes, Easing easing) {
        c().followPath(keyframes, easing);
    }

    public static void lockTo(Supplier<Vec3> target) {
        c().lockTo(target);
    }

    public static void lockTo(Vec3 target) {
        c().lockTo(() -> target);
    }

    public static void lockTo(Entity entity) {
        c().lockTo(() -> entity.getEyePosition());
    }

    public static void release(float durationSeconds) {
        c().release(durationSeconds);
    }

    public static void releaseNow() {
        c().release(0f);
    }
}
