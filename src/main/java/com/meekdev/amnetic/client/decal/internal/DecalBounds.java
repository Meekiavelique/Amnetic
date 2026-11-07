package com.meekdev.amnetic.client.decal.internal;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

public final class DecalBounds {
    private final int[] rect = new int[4];
    private boolean savedScissor;

    private final Vector4f[] clip = new Vector4f[8];
    {
        for (int i = 0; i < 8; i++) clip[i] = new Vector4f();
    }

    public boolean compute(Matrix4f viewProj, Matrix4f boxToWorld, boolean centered, int width, int height) {
        int outLeft = 0, outRight = 0, outBottom = 0, outTop = 0;
        float lo = centered ? -0.5f : 0f, hi = centered ? 0.5f : 1f;
        for (int i = 0; i < 8; i++) {
            Vector4f c = clip[i].set((i & 1) == 0 ? lo : hi, (i & 2) == 0 ? lo : hi, (i & 4) == 0 ? lo : hi, 1f);
            boxToWorld.transform(c);
            viewProj.transform(c);
            if (c.x < -c.w) outLeft++;
            if (c.x > c.w) outRight++;
            if (c.y < -c.w) outBottom++;
            if (c.y > c.w) outTop++;
        }
        if (outLeft == 8 || outRight == 8 || outBottom == 8 || outTop == 8) return false;
        bx0 = Float.MAX_VALUE; by0 = Float.MAX_VALUE; bx1 = -Float.MAX_VALUE; by1 = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vector4f a = clip[i];
            if (a.w > EPS) add(a.x / a.w, a.y / a.w);
            for (int bit = 1; bit < 8; bit <<= 1) {
                if ((i & bit) != 0) continue;
                Vector4f b = clip[i | bit];
                if ((a.w > EPS) == (b.w > EPS)) continue;
                float t = (EPS - a.w) / (b.w - a.w);
                float x = a.x + (b.x - a.x) * t, y = a.y + (b.y - a.y) * t;
                add(x / EPS, y / EPS);
            }
        }
        if (bx0 > bx1) return false;
        int x0 = (int) Math.floor((clampNdc(bx0) * 0.5f + 0.5f) * width) - 1;
        int y0 = (int) Math.floor((clampNdc(by0) * 0.5f + 0.5f) * height) - 1;
        int x1 = (int) Math.ceil((clampNdc(bx1) * 0.5f + 0.5f) * width) + 1;
        int y1 = (int) Math.ceil((clampNdc(by1) * 0.5f + 0.5f) * height) + 1;
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(width, x1); y1 = Math.min(height, y1);
        if (x1 <= x0 || y1 <= y0) return false;
        rect[0] = x0; rect[1] = y0; rect[2] = x1 - x0; rect[3] = y1 - y0;
        return true;
    }

    private static final float EPS = 1e-3f;
    private float bx0, by0, bx1, by1;

    private void add(float x, float y) {
        bx0 = Math.min(bx0, x); bx1 = Math.max(bx1, x);
        by0 = Math.min(by0, y); by1 = Math.max(by1, y);
    }

    private static float clampNdc(float v) { return Math.max(-1f, Math.min(1f, v)); }

    public int[] rect() { return rect; }

    public int pixels() { return rect[2] * rect[3]; }

    public void begin() {
        savedScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
    }

    public void apply() { GL11.glScissor(rect[0], rect[1], rect[2], rect[3]); }

    public void end() {
        if (savedScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
