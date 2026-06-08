package com.meekdev.amnetic.client.anim;

import com.meekdev.amnetic.client.anim.internal.TweenRegistry;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

public final class Animations {

    private static final float MAX_FRAME_DT = 0.1f;
    private static final TweenRegistry REGISTRY = new TweenRegistry();
    private static long lastNano;

    private Animations() {}

    public static <T> Tween<T> tween(T start, T end, float duration, Interpolator<T> interpolator) {
        return new Tween<>(start, end, duration, interpolator);
    }

    public static Tween<Float> tween(float start, float end, float duration) {
        return new Tween<>(start, end, duration, Interpolators.FLOAT);
    }

    public static Tween<Float> tweenAngle(float startDeg, float endDeg, float duration) {
        return new Tween<>(startDeg, endDeg, duration, Interpolators.ANGLE);
    }

    public static Tween<Vector3fc> tween(Vector3fc start, Vector3fc end, float duration) {
        return new Tween<>(start, end, duration, Interpolators.VEC3);
    }

    public static Tween<Vec3> tween(Vec3 start, Vec3 end, float duration) {
        return new Tween<>(start, end, duration, Interpolators.VEC3D);
    }

    public static Tween<Vector4fc> tweenColor(Vector4fc start, Vector4fc end, float duration) {
        return new Tween<>(start, end, duration, Interpolators.COLOR);
    }

    public static Tween<Quaternionfc> tween(Quaternionfc start, Quaternionfc end, float duration) {
        return new Tween<>(start, end, duration, Interpolators.QUATERNION);
    }

    public static Timeline timeline() {
        return new Timeline();
    }

    public static void update() {
        long now = System.nanoTime();
        float dt = lastNano == 0L ? 0f : (now - lastNano) / 1.0e9f;
        lastNano = now;
        if (dt <= 0f) return;
        if (dt > MAX_FRAME_DT) dt = MAX_FRAME_DT;
        REGISTRY.update(dt);
    }

    public static int activeCount() {
        return REGISTRY.activeCount();
    }

    public static void clear() {
        REGISTRY.clear();
    }

    public static TweenRegistry registry() {
        return REGISTRY;
    }
}
