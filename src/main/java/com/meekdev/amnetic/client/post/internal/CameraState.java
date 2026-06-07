package com.meekdev.amnetic.client.post.internal;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class CameraState {

    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW_ROTATION = new Matrix4f();
    private static final Vector3f CAMERA_POS = new Vector3f();
    private static float depthFar = 1024.0f;
    private static volatile boolean valid;

    private CameraState() {}

    public static void update(Matrix4fc projection, Matrix4fc viewRotation,
                              double x, double y, double z, float far) {
        PROJECTION.set(projection);
        VIEW_ROTATION.set(viewRotation);
        CAMERA_POS.set((float) x, (float) y, (float) z);
        depthFar = far;
        valid = true;
    }

    public static boolean valid() {
        return valid;
    }

    public static Matrix4f inverseViewProjection(Matrix4f dest) {
        return dest.set(PROJECTION).mul(VIEW_ROTATION).invert();
    }

    public static Vector3f cameraPos(Vector3f dest) {
        return dest.set(CAMERA_POS);
    }

    public static Matrix4f projection(Matrix4f dest) {
        return dest.set(PROJECTION);
    }

    public static Matrix4f viewRotation(Matrix4f dest) {
        return dest.set(VIEW_ROTATION);
    }

    public static float depthFar() {
        return depthFar;
    }
}
