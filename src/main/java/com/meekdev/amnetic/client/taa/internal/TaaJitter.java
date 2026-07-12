package com.meekdev.amnetic.client.taa.internal;

import org.joml.Matrix4f;

// sub-pixel projection jitter for TAA. Halton(2,3), 8 frame cycle, applied as a clip-space
// translation premultiplied onto the projection so everything consuming the frame's projection
// (terrain, entities, Amnetic geometry, FrameView capture) sees the same jitter
public final class TaaJitter {

    public static final TaaJitter INSTANCE = new TaaJitter();

    private static final int CYCLE = 8;
    private static final float[] HALTON_X = new float[CYCLE];
    private static final float[] HALTON_Y = new float[CYCLE];
    static {
        for (int i = 0; i < CYCLE; i++) {
            HALTON_X[i] = halton(i + 1, 2) - 0.5f;
            HALTON_Y[i] = halton(i + 1, 3) - 0.5f;
        }
    }

    private final Matrix4f scratch = new Matrix4f();
    private int frame;
    private float jitterX, jitterY; // NDC units

    private TaaJitter() {}

    private static float halton(int index, int base) {
        float f = 1f, r = 0f;
        while (index > 0) {
            f /= base;
            r += f * (index % base);
            index /= base;
        }
        return r;
    }

    public void beginFrame(int width, int height) {
        frame = (frame + 1) % CYCLE;
        jitterX = HALTON_X[frame] * 2f / Math.max(1, width);
        jitterY = HALTON_Y[frame] * 2f / Math.max(1, height);
    }

    public float jitterX() { return jitterX; }
    public float jitterY() { return jitterY; }

    public void applyTo(Matrix4f proj) {
        scratch.translation(jitterX, jitterY, 0f).mul(proj, proj);
    }

    public Matrix4f unjitter(Matrix4f viewProj, Matrix4f dest) {
        return scratch.translation(-jitterX, -jitterY, 0f).mul(viewProj, dest);
    }

    public void reset() {
        jitterX = 0f;
        jitterY = 0f;
    }
}
