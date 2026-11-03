package com.meekdev.amnetic.client.light.internal;

import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class SpotlightBounds {
    private static final float MIN_COSINE = .001F;
    private static final float MIN_AXIS_LENGTH_SQUARED = 1e-8F;
    private static final float VERTICAL_AXIS = .9F;
    private static final float MIN_W = .0001F;
    private static final int CORNERS = 5;
    private static final float EDGE_PIXELS = 2F;

    private static final class Scratch {
        final Vector3f axis = new Vector3f();
        final Vector3f u = new Vector3f();
        final Vector3f v = new Vector3f();
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private SpotlightBounds() {}

    public static void project(Matrix4fc matrix, float x, float y, float z, float dx, float dy, float dz,
                               float range, float cosine, int width, int height, float[] bounds) {
        if (cosine <= MIN_COSINE || range <= 0) {
            full(bounds);
            return;
        }
        var s = SCRATCH.get();
        s.axis.set(dx, dy, dz);
        if (s.axis.lengthSquared() < MIN_AXIS_LENGTH_SQUARED) {
            full(bounds);
            return;
        }
        s.axis.normalize();
        boolean upright = Math.abs(s.axis.y) < VERTICAL_AXIS;
        s.u.set(upright ? 0 : 1, upright ? 1 : 0, 0).cross(s.axis).normalize();
        s.v.set(s.axis).cross(s.u);
        float radius = range * (float) Math.sqrt(Math.max(0, 1 - cosine * cosine)) / cosine;
        float minX = Float.POSITIVE_INFINITY;
        float minY = minX;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = maxX;
        for (int corner = 0; corner < CORNERS; corner++) {
            float a = x;
            float b = y;
            float c = z;
            if (corner > 0) {
                float u = (((corner - 1) & 1) == 0 ? -1 : 1) * radius;
                float v = (((corner - 1) & 2) == 0 ? -1 : 1) * radius;
                a += s.axis.x * range + s.u.x * u + s.v.x * v;
                b += s.axis.y * range + s.u.y * u + s.v.y * v;
                c += s.axis.z * range + s.u.z * u + s.v.z * v;
            }
            float w = matrix.m03() * a + matrix.m13() * b + matrix.m23() * c + matrix.m33();
            if (w <= MIN_W || !Float.isFinite(w)) {
                full(bounds);
                return;
            }
            float px = (matrix.m00() * a + matrix.m10() * b + matrix.m20() * c + matrix.m30()) / w;
            float py = (matrix.m01() * a + matrix.m11() * b + matrix.m21() * c + matrix.m31()) / w;
            if (!Float.isFinite(px) || !Float.isFinite(py)) {
                full(bounds);
                return;
            }
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
        }
        bounds[0] = clamp(minX * .5F + .5F - EDGE_PIXELS / width, 0, 1);
        bounds[1] = clamp(minY * .5F + .5F - EDGE_PIXELS / height, 0, 1);
        bounds[2] = clamp(maxX * .5F + .5F + EDGE_PIXELS / width, 0, 1);
        bounds[3] = clamp(maxY * .5F + .5F + EDGE_PIXELS / height, 0, 1);
    }

    public static void full(float[] bounds) {
        bounds[0] = 0;
        bounds[1] = 0;
        bounds[2] = 1;
        bounds[3] = 1;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
