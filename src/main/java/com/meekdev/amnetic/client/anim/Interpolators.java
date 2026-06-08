package com.meekdev.amnetic.client.anim;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class Interpolators {

    private Interpolators() {}

    public static final Interpolator<Float> FLOAT = (a, b, t) -> a + (b - a) * t;

    public static final Interpolator<Float> ANGLE = Interpolators::lerpAngle;

    public static final Interpolator<Vector3fc> VEC3 =
            (a, b, t) -> new Vector3f(a).lerp(b, t);

    public static final Interpolator<Vec3> VEC3D = Interpolators::lerp;

    public static final Interpolator<Vector4fc> COLOR =
            (a, b, t) -> new Vector4f(a).lerp(b, t);

    public static final Interpolator<Quaternionfc> QUATERNION =
            (a, b, t) -> new Quaternionf(a).slerp(b, t);

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float lerpAngle(float a, float b, float t) {
        float delta = ((b - a) % 360f + 540f) % 360f - 180f;
        return a + delta * t;
    }

    public static Vec3 lerp(Vec3 a, Vec3 b, float t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
