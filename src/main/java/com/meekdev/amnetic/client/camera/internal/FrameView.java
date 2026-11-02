package com.meekdev.amnetic.client.camera.internal;

import com.meekdev.amnetic.client.taa.internal.TaaJitter;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;


public final class FrameView {

    public static final FrameView INSTANCE = new FrameView();

    private final Matrix4f view = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private boolean valid;
    private boolean projectionValid;

    private FrameView() {}

    public void set(Matrix4fc m) { view.set(m); valid = true; }

    public void setProjection(Matrix4fc m) { projection.set(m); projectionValid = true; }

    public void invalidate() { valid = false; projectionValid = false; }

    public Matrix4f get(Matrix4f dest, Matrix4fc fallback) {
        return valid ? dest.set(view) : dest.set(fallback);
    }

    public Matrix4f getProjection(Matrix4f dest, Matrix4fc fallback) {
        Matrix4fc source = projectionValid ? projection : fallback;
        return dest.set(source);
    }
}
