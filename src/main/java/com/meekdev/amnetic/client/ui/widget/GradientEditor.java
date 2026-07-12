package com.meekdev.amnetic.client.ui.widget;

import imgui.ImDrawList;
import imgui.ImGui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GradientEditor {

    private static final Map<String, Integer> SELECTED = new HashMap<>();
    private static final Map<String, Integer> DRAG = new HashMap<>();
    private static final float HIT = 8f;

    private GradientEditor() {}

    public static boolean draw(String id, List<float[]> stops, float height) {
        ImGui.pushID(id);
        boolean changed = false;
        float w = Math.max(60f, ImGui.getContentRegionAvailX());
        float x0 = ImGui.getCursorScreenPosX();
        float y0 = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton("bar", w, height);
        boolean hovered = ImGui.isItemHovered();

        stops.sort((a, b) -> Float.compare(a[0], b[0]));
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x0, y0, x0 + w, y0 + height, ImGui.getColorU32(0.1f, 0.1f, 0.1f, 1f));
        if (!stops.isEmpty()) {
            float[] first = stops.get(0), last = stops.get(stops.size() - 1);
            dl.addRectFilled(x0, y0, x0 + first[0] * w, y0 + height, colOf(first));
            dl.addRectFilled(x0 + last[0] * w, y0, x0 + w, y0 + height, colOf(last));
            for (int i = 0; i + 1 < stops.size(); i++) {
                float[] a = stops.get(i), b = stops.get(i + 1);
                int ca = colOf(a), cb = colOf(b);
                dl.addRectFilledMultiColor(x0 + a[0] * w, y0, x0 + b[0] * w, y0 + height, ca, cb, cb, ca);
            }
        }

        float mx = ImGui.getMousePosX(), my = ImGui.getMousePosY();
        if (hovered && ImGui.isMouseClicked(0)) {
            int near = nearest(stops, x0, w, mx);
            if (near >= 0) { SELECTED.put(id, near); DRAG.put(id, near); }
            else {
                float t = clamp01((mx - x0) / w);
                float[] s = sample(stops, t);
                float[] stop = {t, s[0], s[1], s[2]};
                stops.add(stop);
                SELECTED.put(id, stops.indexOf(stop));
                DRAG.put(id, stops.indexOf(stop));
                changed = true;
            }
        }
        if (hovered && ImGui.isMouseClicked(1) && stops.size() > 1) {
            int near = nearest(stops, x0, w, mx);
            if (near >= 0) { stops.remove(near); SELECTED.remove(id); DRAG.remove(id); changed = true; }
        }
        Integer drag = DRAG.get(id);
        if (drag != null) {
            if (ImGui.isMouseDown(0) && drag < stops.size()) { stops.get(drag)[0] = clamp01((mx - x0) / w); changed = true; }
            else DRAG.remove(id);
        }

        for (int i = 0; i < stops.size(); i++) {
            float sxp = x0 + stops.get(i)[0] * w;
            boolean sel = Integer.valueOf(i).equals(SELECTED.get(id));
            int mc = ImGui.getColorU32(sel ? 1f : 0.8f, sel ? 0.85f : 0.8f, sel ? 0.2f : 0.8f, 1f);
            dl.addTriangleFilled(sxp - 5, y0 + height, sxp + 5, y0 + height, sxp, y0 + height - 7, mc);
        }

        ImGui.popID();

        Integer sel = SELECTED.get(id);
        if (sel != null && sel < stops.size()) {
            float[] s = stops.get(sel);
            float[] rgb = {s[1], s[2], s[3]};
            if (ImGui.colorEdit3(id + " stop", rgb)) { s[1] = rgb[0]; s[2] = rgb[1]; s[3] = rgb[2]; changed = true; }
        }
        return changed;
    }

    private static int colOf(float[] s) { return ImGui.getColorU32(s[1], s[2], s[3], 1f); }

    private static int nearest(List<float[]> stops, float x0, float w, float mx) {
        int best = -1; float bestD = HIT;
        for (int i = 0; i < stops.size(); i++) {
            float d = Math.abs((x0 + stops.get(i)[0] * w) - mx);
            if (d <= bestD) { bestD = d; best = i; }
        }
        return best;
    }

    private static float[] sample(List<float[]> stops, float t) {
        if (stops.isEmpty()) return new float[]{1f, 1f, 1f};
        float[] prev = stops.get(0), next = stops.get(stops.size() - 1);
        for (int i = 0; i + 1 < stops.size(); i++) {
            if (t >= stops.get(i)[0] && t <= stops.get(i + 1)[0]) { prev = stops.get(i); next = stops.get(i + 1); break; }
        }
        float span = Math.max(1e-4f, next[0] - prev[0]);
        float f = clamp01((t - prev[0]) / span);
        return new float[]{lerp(prev[1], next[1], f), lerp(prev[2], next[2], f), lerp(prev[3], next[3], f)};
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
