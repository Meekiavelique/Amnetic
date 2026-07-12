package com.meekdev.amnetic.client.ui.widget;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

/**
 * shared editor controls so every panel looks and behaves the same
 * (two-column label | control rows, coloured vec3 fields, uniform sliders/toggles)
 */
public final class Widgets {

    private static final float LABEL_W = 116f;
    private static final int X_COL = 0x40_4D5DF2; // faint axis tints (ABGR, low alpha)
    private static final int Y_COL = 0x40_4DF26A;
    private static final int Z_COL = 0x40_F2A04D;

    private Widgets() {}

    /** label on the left column, next item fills the remaining width */
    private static void label(String text) {
        ImGui.alignTextToFramePadding();
        ImGui.text(text);
        ImGui.sameLine(LABEL_W);
        ImGui.setNextItemWidth(-1f);
    }

    public static boolean dragFloat(String lbl, float[] v, float speed, float min, float max) {
        label(lbl);
        return ImGui.dragFloat("##" + lbl, v, speed, min, max);
    }

    public static boolean slider(String lbl, float[] v, float min, float max) {
        label(lbl);
        return ImGui.sliderFloat("##" + lbl, v, min, max);
    }

    public static boolean sliderInt(String lbl, int[] v, int min, int max) {
        label(lbl);
        return ImGui.sliderInt("##" + lbl, v, min, max);
    }

    public static boolean dragInt(String lbl, int[] v, float speed, int min, int max) {
        label(lbl);
        return ImGui.dragInt("##" + lbl, v, speed, min, max);
    }

    public static boolean checkbox(String lbl, ImBoolean v) {
        label(lbl);
        return ImGui.checkbox("##" + lbl, v);
    }

    public static boolean color3(String lbl, float[] rgb) {
        label(lbl);
        return ImGui.colorEdit3("##" + lbl, rgb);
    }

    public static boolean color4(String lbl, float[] rgba) {
        label(lbl);
        return ImGui.colorEdit4("##" + lbl, rgba);
    }

    public static boolean combo(String lbl, ImInt sel, String[] items) {
        label(lbl);
        return ImGui.combo("##" + lbl, sel, items);
    }

    /** three colour-tinted X/Y/Z drag fields on one row */
    public static boolean vec3(String lbl, float[] xyz, float speed) {
        label(lbl);
        boolean changed = false;
        float w = (ImGui.getContentRegionAvailX() - 12f) / 3f;
        changed |= axisField(lbl + "x", 0, xyz, w, speed, X_COL);
        ImGui.sameLine(0f, 6f);
        changed |= axisField(lbl + "y", 1, xyz, w, speed, Y_COL);
        ImGui.sameLine(0f, 6f);
        changed |= axisField(lbl + "z", 2, xyz, w, speed, Z_COL);
        return changed;
    }

    private static boolean axisField(String id, int idx, float[] xyz, float w, float speed, int tint) {
        float[] tmp = {xyz[idx]};
        ImGui.setNextItemWidth(w);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, tint);
        boolean c = ImGui.dragFloat("##" + id, tmp, speed);
        ImGui.popStyleColor();
        if (c) xyz[idx] = tmp[0];
        return c;
    }

    /** collapsible section header, open by default */
    public static boolean section(String title) {
        return ImGui.collapsingHeader(title, ImGuiTreeNodeFlags.DefaultOpen);
    }

}
