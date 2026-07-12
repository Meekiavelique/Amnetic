package com.meekdev.amnetic.client.ui;

import com.meekdev.amnetic.client.render.CameraSnapshot;
import imgui.ImDrawList;
import org.joml.Vector4f;

public final class Gizmos {

    private Gizmos() {}

    public static float[] project(CameraSnapshot cam, Vector4f tmp, double wx, double wy, double wz, float w, float h) {
        tmp.set((float) (wx - cam.eye.x), (float) (wy - cam.eye.y), (float) (wz - cam.eye.z), 1f);
        cam.viewProj.transform(tmp);
        if (tmp.w <= 1e-4f) return null;
        float ndcX = tmp.x / tmp.w, ndcY = tmp.y / tmp.w;
        return new float[]{(ndcX * 0.5f + 0.5f) * w, (1f - (ndcY * 0.5f + 0.5f)) * h};
    }

    public static int col(float r, float g, float b, float a) {
        return (c(a) << 24) | (c(b) << 16) | (c(g) << 8) | c(r);
    }

    private static int c(float v) { return Math.max(0, Math.min(255, Math.round(v * 255f))); }

    public static void ring(ImDrawList dl, CameraSnapshot cam, Vector4f tmp, float w, float h, int color,
                            double cx, double cy, double cz,
                            float ax, float ay, float az, float bx, float by, float bz, int segments) {
        float[] prev = null, first = null;
        for (int i = 0; i <= segments; i++) {
            double t = (i / (double) segments) * Math.PI * 2.0;
            float cs = (float) Math.cos(t), sn = (float) Math.sin(t);
            float[] s = project(cam, tmp,
                    cx + ax * cs + bx * sn, cy + ay * cs + by * sn, cz + az * cs + bz * sn, w, h);
            if (s != null && prev != null) dl.addLine(prev[0], prev[1], s[0], s[1], color, 1.2f);
            if (i == 0) first = s;
            prev = s;
        }
        if (first != null && prev != null) dl.addLine(prev[0], prev[1], first[0], first[1], color, 1.2f);
    }
}
