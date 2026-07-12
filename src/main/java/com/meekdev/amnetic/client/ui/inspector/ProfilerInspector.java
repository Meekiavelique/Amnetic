package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.pipeline.PassProfiler;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.ui.Inspector;
import imgui.ImGui;
import java.util.List;
import java.util.Map;

public final class ProfilerInspector extends Inspector {

    public ProfilerInspector() {
        super("Info", "Profiler", false);
    }

    @Override
    public void render() {
        Map<RenderStage, List<PassProfiler.Entry>> snapshot = PassProfiler.INSTANCE.snapshot();

        float cpuTotal = 0f;
        float gpuTotal = 0f;
        boolean anyGpu = false;
        for (RenderStage stage : RenderStage.values()) {
            List<PassProfiler.Entry> entries = snapshot.get(stage);
            if (entries.isEmpty()) continue;

            float stageCpu = 0f;
            float stageGpu = 0f;
            boolean stageHasGpu = false;
            for (PassProfiler.Entry e : entries) {
                stageCpu += e.avgMs;
                if (e.avgGpuMs >= 0f) { stageGpu += e.avgGpuMs; stageHasGpu = true; }
            }
            cpuTotal += stageCpu;
            if (stageHasGpu) { gpuTotal += stageGpu; anyGpu = true; }

            String header = stageHasGpu
                    ? String.format("%s  (cpu %.2f ms / gpu %.2f ms)###%s", stage.name(), stageCpu, stageGpu, stage.name())
                    : String.format("%s  (cpu %.2f ms)###%s", stage.name(), stageCpu, stage.name());
            if (ImGui.collapsingHeader(header)) {
                for (PassProfiler.Entry e : entries) {
                    if (e.avgGpuMs >= 0f) {
                        ImGui.text(String.format("%-28s cpu %6.2f ms (last %6.2f)   gpu %6.2f ms (last %6.2f)",
                                e.label, e.avgMs, e.lastMs, e.avgGpuMs, e.lastGpuMs));
                    } else {
                        ImGui.text(String.format("%-28s cpu %6.2f ms (last %6.2f)   gpu (no result yet)",
                                e.label, e.avgMs, e.lastMs));
                    }
                }
            }
        }

        ImGui.separator();
        ImGui.text(String.format("Amnetic CPU total: %.2f ms (%.0f fps if this were the whole frame)",
                cpuTotal, cpuTotal > 0f ? 1000f / cpuTotal : 0f));
        if (anyGpu) {
            ImGui.text(String.format("Amnetic GPU total: %.2f ms (%.0f fps if this were the whole frame)",
                    gpuTotal, gpuTotal > 0f ? 1000f / gpuTotal : 0f));
        }
    }
}
