package com.meekdev.amnetic.client.ui.widget;

import imgui.ImDrawList;
import imgui.ImGui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CurveEditor {

    private static final Map<String, Integer> DRAG = new HashMap<>();
    private static final float HIT = 7f;

    private CurveEditor() {}

    public static boolean draw(String id, List<float[]> keys, float vmin, float vmax, float height) {
        ImGui.pushID(id);
        boolean changed = false;
        float w = Math.max(60f, ImGui.getContentRegionAvailX());
        float x0 = ImGui.getCursorScreenPosX();
        float y0 = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton("canvas", w, height);
        boolean hovered = ImGui.isItemHovered();

        ImDrawList dl = ImGui.getWindowDrawList();
        int bg = ImGui.getColorU32(0.10f, 0.10f, 0.13f, 1f);
        int gridC = ImGui.getColorU32(1f, 1f, 1f, 0.07f);
        int lineC = ImGui.getColorU32(0.4f, 0.8f, 1f, 1f);
        int ptC = ImGui.getColorU32(1f, 1f, 1f, 1f);
        dl.addRectFilled(x0, y0, x0 + w, y0 + height, bg);
        for (int i = 0; i <= 4; i++) {
            float gx = x0 + w * i / 4f, gy = y0 + height * i / 4f;
            dl.addLine(gx, y0, gx, y0 + height, gridC);
            dl.addLine(x0, gy, x0 + w, gy, gridC);
        }

        keys.sort((a, b) -> Float.compare(a[0], b[0]));
        float span = Math.max(1e-4f, vmax - vmin);

        for (int i = 0; i + 1 < keys.size(); i++) {
            float[] a = keys.get(i), b = keys.get(i + 1);
            dl.addLine(sx(x0, w, a[0]), sy(y0, height, a[1], vmin, span),
                    sx(x0, w, b[0]), sy(y0, height, b[1], vmin, span), lineC, 2f);
        }

        float mx = ImGui.getMousePosX(), my = ImGui.getMousePosY();

        if (hovered && ImGui.isMouseClicked(0)) {
            int near = nearest(keys, x0, y0, w, height, vmin, span, mx, my);
            if (near >= 0) {
                DRAG.put(id, near);
            } else {
                float[] k = {clamp01((mx - x0) / w), clampV(vmin, vmax, vmin + (1f - (my - y0) / height) * span)};
                keys.add(k);
                DRAG.put(id, keys.indexOf(k));
                changed = true;
            }
        }
        if (hovered && ImGui.isMouseClicked(1) && keys.size() > 1) {
            int near = nearest(keys, x0, y0, w, height, vmin, span, mx, my);
            if (near >= 0) { keys.remove(near); changed = true; DRAG.remove(id); }
        }

        Integer dragIdx = DRAG.get(id);
        if (dragIdx != null) {
            if (ImGui.isMouseDown(0) && dragIdx < keys.size()) {
                float[] k = keys.get(dragIdx);
                k[0] = clamp01((mx - x0) / w);
                k[1] = clampV(vmin, vmax, vmin + (1f - (my - y0) / height) * span);
                changed = true;
            } else {
                DRAG.remove(id);
            }
        }

        for (float[] k : keys) {
            dl.addCircleFilled(sx(x0, w, k[0]), sy(y0, height, k[1], vmin, span), 4f, ptC);
        }

        ImGui.popID();
        return changed;
    }

    private static int nearest(List<float[]> keys, float x0, float y0, float w, float h,
                               float vmin, float span, float mx, float my) {
        int best = -1;
        float bestD = HIT * HIT;
        for (int i = 0; i < keys.size(); i++) {
            float[] k = keys.get(i);
            float dx = sx(x0, w, k[0]) - mx, dy = sy(y0, h, k[1], vmin, span) - my;
            float d = dx * dx + dy * dy;
            if (d <= bestD) { bestD = d; best = i; }
        }
        return best;
    }

    private static float sx(float x0, float w, float t) { return x0 + clamp01(t) * w; }
    private static float sy(float y0, float h, float v, float vmin, float span) { return y0 + (1f - (v - vmin) / span) * h; }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float clampV(float lo, float hi, float v) { return Math.max(lo, Math.min(hi, v)); }
}
