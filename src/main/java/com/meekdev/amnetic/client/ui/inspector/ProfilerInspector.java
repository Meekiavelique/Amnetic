package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.pipeline.PassProfiler;
import com.meekdev.amnetic.client.ui.Inspector;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import java.util.List;

public final class ProfilerInspector extends Inspector {
    private static final float BAR_HEIGHT = 5f;

    public ProfilerInspector() {
        super("Info", "Profiler", false);
    }

    @Override
    public void render() {
        PassProfiler profiler = PassProfiler.INSTANCE;
        profiler.watch();
        List<PassProfiler.Entry> entries = profiler.sorted();

        float cpuTotal = 0f;
        float gpuTotal = 0f;
        float largest = 0f;
        for (PassProfiler.Entry e : entries) {
            cpuTotal += e.avgMs;
            if (e.avgGpuMs > 0f) gpuTotal += e.avgGpuMs;
            largest = Math.max(largest, e.costMs());
        }
        float frame = profiler.frameMs();
        ImGui.text(frame > 0f ? String.format("frame %.2f ms (%.0f fps)", frame, 1000f / frame) : "frame -");
        ImGui.sameLine();
        ImGui.textColored(0.45f, 0.7f, 1f, 1f, String.format("cpu %.2f ms", cpuTotal));
        ImGui.sameLine();
        ImGui.textColored(1f, 0.6f, 0.25f, 1f, String.format("gpu %.2f ms", gpuTotal));
        if (entries.isEmpty()) {
            ImGui.textDisabled("waiting for the first frames");
            return;
        }

        float scale = Math.max(frame, largest);
        int flags = ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.BordersInnerV;
        if (!ImGui.beginTable("amnetic.profiler", 5, flags)) return;
        ImGui.tableSetupColumn("pass", ImGuiTableColumnFlags.WidthStretch, 3f);
        ImGui.tableSetupColumn("stage", ImGuiTableColumnFlags.WidthStretch, 1.6f);
        ImGui.tableSetupColumn("cpu ms", ImGuiTableColumnFlags.WidthFixed, 56f);
        ImGui.tableSetupColumn("gpu ms", ImGuiTableColumnFlags.WidthFixed, 56f);
        ImGui.tableSetupColumn("share", ImGuiTableColumnFlags.WidthStretch, 2f);
        ImGui.tableHeadersRow();
        for (PassProfiler.Entry e : entries) {
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.text(e.label);
            ImGui.tableNextColumn();
            ImGui.textDisabled(e.stage == null ? "other" : e.stage.name().toLowerCase());
            ImGui.tableNextColumn();
            ImGui.text(String.format("%.2f", e.avgMs));
            ImGui.tableNextColumn();
            if (e.avgGpuMs >= 0f) ImGui.text(String.format("%.2f", e.avgGpuMs));
            else ImGui.textDisabled("-");
            ImGui.tableNextColumn();
            bars(e, scale);
        }
        ImGui.endTable();
    }

    private static void bars(PassProfiler.Entry e, float scale) {
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY() + 2f;
        float width = ImGui.getContentRegionAvailX();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + width, y + BAR_HEIGHT * 2 + 1, ImGui.getColorU32(1f, 1f, 1f, 0.06f));
        if (scale > 0f) {
            dl.addRectFilled(x, y, x + width * Math.min(1f, e.avgMs / scale), y + BAR_HEIGHT,
                    ImGui.getColorU32(0.45f, 0.7f, 1f, 1f));
            if (e.avgGpuMs > 0f) {
                dl.addRectFilled(x, y + BAR_HEIGHT + 1, x + width * Math.min(1f, e.avgGpuMs / scale), y + BAR_HEIGHT * 2 + 1,
                        ImGui.getColorU32(1f, 0.6f, 0.25f, 1f));
            }
        }
        ImGui.dummy(width, BAR_HEIGHT * 2 + 3);
    }
}
