package com.meekdev.amnetic.client.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// timing per registered pass, keyed by stage + label: CPU wall-clock around pass.render (submit time)
// and actual GPU execution time via a timer query. these can diverge a lot - GL calls queue async, so a
// GPU-bound pass looks nearly free on the CPU timeline while it's the real bottleneck
public final class PassProfiler {

    public static final PassProfiler INSTANCE = new PassProfiler();

    // EMA weight for the rolling average, low enough that spikes stay visible
    private static final float SMOOTHING = 0.9f;

    public static final class Entry {
        public final RenderStage stage;
        public final String label;
        public volatile float lastMs;
        public volatile float avgMs;
        public volatile float lastGpuMs = -1f;
        public volatile float avgGpuMs = -1f;

        private Entry(RenderStage stage, String label) {
            this.stage = stage;
            this.label = label;
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    private PassProfiler() {}

    void record(RenderStage stage, String label, long nanos) {
        float ms = nanos / 1_000_000f;
        Entry e = entries.computeIfAbsent(stage + "/" + label, k -> new Entry(stage, label));
        e.lastMs = ms;
        e.avgMs = e.avgMs <= 0f ? ms : e.avgMs * SMOOTHING + ms * (1f - SMOOTHING);
    }

    void recordGpu(RenderStage stage, String label, float ms) {
        Entry e = entries.computeIfAbsent(stage + "/" + label, k -> new Entry(stage, label));
        e.lastGpuMs = ms;
        e.avgGpuMs = e.avgGpuMs <= 0f ? ms : e.avgGpuMs * SMOOTHING + ms * (1f - SMOOTHING);
    }

    // snapshot of all recorded entries, grouped by stage in declaration order
    public Map<RenderStage, List<Entry>> snapshot() {
        Map<RenderStage, List<Entry>> out = new LinkedHashMap<>();
        for (RenderStage s : RenderStage.values()) out.put(s, new ArrayList<>());
        for (Entry e : entries.values()) out.get(e.stage).add(e);
        for (List<Entry> list : out.values()) {
            list.sort((a, b) -> a.label.compareTo(b.label));
        }
        return out;
    }
}
