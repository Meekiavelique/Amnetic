package com.meekdev.amnetic.client.ui;

import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.LightType;
import com.meekdev.amnetic.client.light.internal.LightRegistry;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.ui.scene.EditorSelection;
import imgui.ImDrawList;
import imgui.ImGui;
import org.joml.Vector4f;

public final class LightGizmo {

    public static boolean enabled = true;
    public static Light selected;

    private static final float HIT_RADIUS = 9f;

    private LightGizmo() {}

    public static void draw() {
        if (!enabled) return;
        CameraSnapshot cam = CameraSnapshot.current();
        if (cam == null) return;
        float w = ImGui.getIO().getDisplaySizeX(), h = ImGui.getIO().getDisplaySizeY();
        if (w <= 0 || h <= 0) return;

        float mx = ImGui.getMousePosX(), my = ImGui.getMousePosY();
        boolean clickable = ImGui.isMouseClicked(0) && !ImGui.getIO().getWantCaptureMouse();
        Light hit = null;
        float hitD2 = HIT_RADIUS * HIT_RADIUS;

        ImDrawList dl = ImGui.getBackgroundDrawList(); // behind panels, over the world
        Vector4f tmp = new Vector4f();
        int idx = 0;
        for (Light light : LightRegistry.INSTANCE.all()) {
            if (!light.isEnabled()) { idx++; continue; }
            float[] s = Gizmos.project(cam, tmp, light.x(), light.y(), light.z(), w, h);
            if (s == null) { idx++; continue; }

            int fill = Gizmos.col(light.red(), light.green(), light.blue(), 1f);
            int outline = Gizmos.col(1f, 1f, 1f, 0.9f);
            int shape = Gizmos.col(light.red(), light.green(), light.blue(), 0.5f);
            boolean isSelected = EditorSelection.is(light);

            float d2 = (mx - s[0]) * (mx - s[0]) + (my - s[1]) * (my - s[1]);
            boolean hover = d2 <= HIT_RADIUS * HIT_RADIUS;
            if (hover && d2 <= hitD2) { hit = light; hitD2 = d2; }

            drawShape(dl, cam, tmp, w, h, light, s, shape, outline);

            float r = (hover || isSelected) ? 8f : 6f;
            dl.addCircleFilled(s[0], s[1], r, fill);
            dl.addCircle(s[0], s[1], r, outline, 16, 1.5f);
            if (isSelected) dl.addCircle(s[0], s[1], r + 4f, Gizmos.col(1f, 0.85f, 0.2f, 1f), 20, 2f);
            dl.addText(s[0] + 9f, s[1] - 7f, outline, light.type().name().toLowerCase() + " #" + idx);
            idx++;
        }

        // click-select feeds the shared selection used by the transform gizmo
        // skipped mid handle-drag so dragging doesn't re-pick, empty click clears
        if (clickable && !GizmoLayer.isDragging()) {
            selected = hit;
            EditorSelection.setTarget(hit);
        }
    }

    private static void drawShape(ImDrawList dl, CameraSnapshot cam, Vector4f tmp, float w, float h,
                                  Light light, float[] s, int shape, int outline) {
        LightType type = light.type();
        float dx = light.dirX(), dy = light.dirY(), dz = light.dirZ();
        float range = light.range();

        switch (type) {
            case POINT -> {
                Gizmos.ring(dl, cam, tmp, w, h, shape, light.x(), light.y(), light.z(),
                        range, 0, 0, 0, 0, range, 24); // XZ
                Gizmos.ring(dl, cam, tmp, w, h, shape, light.x(), light.y(), light.z(),
                        range, 0, 0, 0, range, 0, 24); // XY
                Gizmos.ring(dl, cam, tmp, w, h, shape, light.x(), light.y(), light.z(),
                        0, range, 0, 0, 0, range, 24); // YZ
            }
            case SPOT -> {
                float outer = (float) Math.acos(Math.max(-1f, Math.min(1f, light.cosOuter())));
                float baseR = (float) (range * Math.tan(outer));
                double bx = light.x() + dx * range, by = light.y() + dy * range, bz = light.z() + dz * range;
                float[] rt = perp(dx, dy, dz, true), up = perp(dx, dy, dz, false);
                Gizmos.ring(dl, cam, tmp, w, h, shape, bx, by, bz,
                        rt[0] * baseR, rt[1] * baseR, rt[2] * baseR, up[0] * baseR, up[1] * baseR, up[2] * baseR, 24);
                for (int k = 0; k < 4; k++) {
                    double t = k / 4.0 * Math.PI * 2.0;
                    float cs = (float) Math.cos(t), sn = (float) Math.sin(t);
                    float[] e = Gizmos.project(cam, tmp,
                            bx + (rt[0] * cs + up[0] * sn) * baseR,
                            by + (rt[1] * cs + up[1] * sn) * baseR,
                            bz + (rt[2] * cs + up[2] * sn) * baseR, w, h);
                    if (e != null) dl.addLine(s[0], s[1], e[0], e[1], shape, 1.2f);
                }
            }
            case AREA_DISC -> {
                float[] rt = perp(dx, dy, dz, true), up = perp(dx, dy, dz, false);
                float rad = light.areaW();
                Gizmos.ring(dl, cam, tmp, w, h, shape, light.x(), light.y(), light.z(),
                        rt[0] * rad, rt[1] * rad, rt[2] * rad, up[0] * rad, up[1] * rad, up[2] * rad, 24);
            }
            case AREA_RECT -> {
                float[] rt = {light.tanX(), light.tanY(), light.tanZ()};
                float[] up = cross(dx, dy, dz, rt[0], rt[1], rt[2]);
                float aw = light.areaW(), ah = light.areaH();
                drawRect(dl, cam, tmp, w, h, shape, light.x(), light.y(), light.z(), rt, up, aw, ah);
            }
            case TUBE -> {
                float hx = light.tanX() * light.tubeLen() * 0.5f;
                float hy = light.tanY() * light.tubeLen() * 0.5f;
                float hz = light.tanZ() * light.tubeLen() * 0.5f;
                float[] a = Gizmos.project(cam, tmp, light.x() - hx, light.y() - hy, light.z() - hz, w, h);
                float[] b = Gizmos.project(cam, tmp, light.x() + hx, light.y() + hy, light.z() + hz, w, h);
                if (a != null && b != null) dl.addLine(a[0], a[1], b[0], b[1], shape, 2f);
            }
            default -> { }
        }

        if (type == LightType.SPOT || type == LightType.DIRECTIONAL
                || type == LightType.AREA_RECT || type == LightType.AREA_DISC) {
            float len = type == LightType.DIRECTIONAL ? 4f : Math.min(range, 5f);
            float[] tip = Gizmos.project(cam, tmp, light.x() + dx * len, light.y() + dy * len, light.z() + dz * len, w, h);
            if (tip != null) dl.addLine(s[0], s[1], tip[0], tip[1], outline, 1.2f);
        }
    }

    private static void drawRect(ImDrawList dl, CameraSnapshot cam, Vector4f tmp, float w, float h, int color,
                                 double cx, double cy, double cz, float[] rt, float[] up, float aw, float ah) {
        float[][] c = new float[4][];
        int[][] sgn = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int i = 0; i < 4; i++) {
            c[i] = Gizmos.project(cam, tmp,
                    cx + rt[0] * aw * sgn[i][0] + up[0] * ah * sgn[i][1],
                    cy + rt[1] * aw * sgn[i][0] + up[1] * ah * sgn[i][1],
                    cz + rt[2] * aw * sgn[i][0] + up[2] * ah * sgn[i][1], w, h);
        }
        for (int i = 0; i < 4; i++) {
            float[] a = c[i], b = c[(i + 1) % 4];
            if (a != null && b != null) dl.addLine(a[0], a[1], b[0], b[1], color, 1.2f);
        }
    }

    private static float[] perp(float dx, float dy, float dz, boolean first) {
        float ux = Math.abs(dy) > 0.9f ? 1f : 0f, uy = Math.abs(dy) > 0.9f ? 0f : 1f, uz = 0f;
        float[] right = norm(cross(dx, dy, dz, ux, uy, uz));
        if (first) return right;
        return norm(cross(right[0], right[1], right[2], dx, dy, dz));
    }

    private static float[] cross(float ax, float ay, float az, float bx, float by, float bz) {
        return new float[]{ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
    }

    private static float[] norm(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (l < 1e-6f) return new float[]{1f, 0f, 0f};
        return new float[]{v[0] / l, v[1] / l, v[2] / l};
    }
}
