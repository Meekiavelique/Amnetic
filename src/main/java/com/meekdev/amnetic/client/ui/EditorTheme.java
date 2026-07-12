package com.meekdev.amnetic.client.ui;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiDir;

public final class EditorTheme {

    private EditorTheme() {}

    public static final float[] ACCENT = {0.26f, 0.72f, 0.85f};
    public static final float[] ACCENT_DIM = {0.20f, 0.52f, 0.62f};
    public static final int AXIS_X = 0xFF4D5DF2;
    public static final int AXIS_Y = 0xFF4DF26A;
    public static final int AXIS_Z = 0xFFF2A04D;

    static void apply() {
        ImGuiStyle s = ImGui.getStyle();

        s.setWindowPadding(10f, 8f);
        s.setFramePadding(8f, 4f);
        s.setCellPadding(4f, 3f);
        s.setItemSpacing(8f, 6f);
        s.setItemInnerSpacing(6f, 5f);
        s.setIndentSpacing(16f);
        s.setScrollbarSize(12f);
        s.setGrabMinSize(11f);

        s.setWindowBorderSize(1f);
        s.setChildBorderSize(1f);
        s.setPopupBorderSize(1f);
        s.setFrameBorderSize(0f);
        s.setTabBorderSize(0f);

        s.setWindowRounding(0f);
        s.setChildRounding(0f);
        s.setFrameRounding(0f);
        s.setPopupRounding(0f);
        s.setScrollbarRounding(0f);
        s.setGrabRounding(0f);
        s.setTabRounding(0f);
        s.setLogSliderDeadzone(4f);

        s.setWindowMenuButtonPosition(ImGuiDir.Right);
        s.setWindowTitleAlign(0.02f, 0.5f);

        float[] A = ACCENT, AD = ACCENT_DIM;
        col(ImGuiCol.Text, 0.92f, 0.93f, 0.94f, 1.00f);
        col(ImGuiCol.TextDisabled, 0.45f, 0.47f, 0.50f, 1.00f);
        col(ImGuiCol.WindowBg, 0.11f, 0.115f, 0.13f, 0.96f);
        col(ImGuiCol.ChildBg, 0.13f, 0.135f, 0.15f, 0.40f);
        col(ImGuiCol.PopupBg, 0.09f, 0.095f, 0.11f, 0.98f);
        col(ImGuiCol.Border, 0.00f, 0.00f, 0.00f, 0.40f);
        col(ImGuiCol.BorderShadow, 0.00f, 0.00f, 0.00f, 0.00f);
        col(ImGuiCol.FrameBg, 0.17f, 0.18f, 0.20f, 1.00f);
        col(ImGuiCol.FrameBgHovered, 0.22f, 0.23f, 0.26f, 1.00f);
        col(ImGuiCol.FrameBgActive, 0.24f, 0.26f, 0.30f, 1.00f);
        col(ImGuiCol.TitleBg, 0.09f, 0.095f, 0.11f, 1.00f);
        col(ImGuiCol.TitleBgActive, 0.13f, 0.14f, 0.16f, 1.00f);
        col(ImGuiCol.TitleBgCollapsed, 0.09f, 0.095f, 0.11f, 0.80f);
        col(ImGuiCol.MenuBarBg, 0.13f, 0.14f, 0.16f, 1.00f);
        col(ImGuiCol.ScrollbarBg, 0.00f, 0.00f, 0.00f, 0.20f);
        col(ImGuiCol.ScrollbarGrab, 0.26f, 0.27f, 0.30f, 1.00f);
        col(ImGuiCol.ScrollbarGrabHovered, 0.33f, 0.34f, 0.38f, 1.00f);
        col(ImGuiCol.ScrollbarGrabActive, A[0], A[1], A[2], 1.00f);
        col(ImGuiCol.CheckMark, A[0], A[1], A[2], 1.00f);
        col(ImGuiCol.SliderGrab, AD[0], AD[1], AD[2], 1.00f);
        col(ImGuiCol.SliderGrabActive, A[0], A[1], A[2], 1.00f);
        col(ImGuiCol.Button, 0.20f, 0.21f, 0.24f, 1.00f);
        col(ImGuiCol.ButtonHovered, 0.26f, 0.28f, 0.32f, 1.00f);
        col(ImGuiCol.ButtonActive, AD[0], AD[1], AD[2], 1.00f);
        col(ImGuiCol.Header, 0.20f, 0.21f, 0.24f, 1.00f);
        col(ImGuiCol.HeaderHovered, 0.26f, 0.28f, 0.32f, 1.00f);
        col(ImGuiCol.HeaderActive, AD[0], AD[1], AD[2], 0.85f);
        col(ImGuiCol.Separator, 0.00f, 0.00f, 0.00f, 0.40f);
        col(ImGuiCol.SeparatorHovered, A[0], A[1], A[2], 0.60f);
        col(ImGuiCol.SeparatorActive, A[0], A[1], A[2], 1.00f);
        col(ImGuiCol.ResizeGrip, 0.26f, 0.27f, 0.30f, 0.60f);
        col(ImGuiCol.ResizeGripHovered, A[0], A[1], A[2], 0.70f);
        col(ImGuiCol.ResizeGripActive, A[0], A[1], A[2], 1.00f);
        col(ImGuiCol.Tab, 0.13f, 0.14f, 0.16f, 1.00f);
        col(ImGuiCol.TabHovered, 0.24f, 0.26f, 0.30f, 1.00f);
        col(ImGuiCol.TabActive, 0.20f, 0.30f, 0.34f, 1.00f);
        col(ImGuiCol.TabUnfocused, 0.11f, 0.115f, 0.13f, 1.00f);
        col(ImGuiCol.TabUnfocusedActive, 0.15f, 0.17f, 0.19f, 1.00f);
        col(ImGuiCol.PlotLines, A[0], A[1], A[2], 1.00f);
        col(ImGuiCol.PlotLinesHovered, 1.00f, 0.60f, 0.30f, 1.00f);
        col(ImGuiCol.PlotHistogram, A[0], A[1], A[2], 1.00f);
        col(ImGuiCol.PlotHistogramHovered, 1.00f, 0.60f, 0.30f, 1.00f);
        col(ImGuiCol.TableHeaderBg, 0.15f, 0.16f, 0.18f, 1.00f);
        col(ImGuiCol.TableBorderStrong, 0.00f, 0.00f, 0.00f, 0.50f);
        col(ImGuiCol.TableBorderLight, 0.00f, 0.00f, 0.00f, 0.30f);
        col(ImGuiCol.TableRowBg, 0.00f, 0.00f, 0.00f, 0.00f);
        col(ImGuiCol.TableRowBgAlt, 1.00f, 1.00f, 1.00f, 0.03f);
        col(ImGuiCol.TextSelectedBg, A[0], A[1], A[2], 0.35f);
        col(ImGuiCol.NavWindowingHighlight, 1.00f, 1.00f, 1.00f, 0.70f);
        col(ImGuiCol.NavWindowingDimBg, 0.80f, 0.80f, 0.80f, 0.20f);
        col(ImGuiCol.ModalWindowDimBg, 0.05f, 0.05f, 0.06f, 0.60f);
    }

    private static void col(int key, float r, float g, float b, float a) {
        ImGui.getStyle().setColor(key, r, g, b, a);
    }
}
