package com.meekdev.amnetic.client.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PassProfiler {
    public static final PassProfiler INSTANCE = new PassProfiler();

    private static final float SMOOTHING = 0.9f;
    private static final long WATCH_NANOS = 2_000_000_000L;
    private static final long STALE_NANOS = 1_000_000_000L;

    public static final class Entry {
        public final RenderStage stage;
        public final String label;
        public volatile float lastMs;
        public volatile float avgMs;
        public volatile float lastGpuMs = -1f;
        public volatile float avgGpuMs = -1f;
        volatile long updated;

        private Entry(RenderStage stage, String label) {
            this.stage = stage;
            this.label = label;
        }

        public float costMs() {
            return Math.max(avgMs, avgGpuMs);
        }

        void cpu(long nanos) {
            float ms = nanos / 1_000_000f;
            lastMs = ms;
            avgMs = avgMs <= 0f ? ms : avgMs * SMOOTHING + ms * (1f - SMOOTHING);
            updated = System.nanoTime();
        }

        void gpu(float ms) {
            lastGpuMs = ms;
            avgGpuMs = avgGpuMs <= 0f ? ms : avgGpuMs * SMOOTHING + ms * (1f - SMOOTHING);
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private volatile long watchedAt = Long.MIN_VALUE / 2;
    private volatile long frameStart;
    private volatile float frameMs;

    private PassProfiler() {}

    public void watch() {
        watchedAt = System.nanoTime();
    }

    public boolean active() {
        return Pipeline.gpuTimings() || System.nanoTime() - watchedAt < WATCH_NANOS;
    }

    Entry entry(RenderStage stage, String label) {
        return entries.computeIfAbsent(stage + "/" + label, k -> new Entry(stage, label));
    }

    public void record(String label, long nanos) {
        entries.computeIfAbsent(label, k -> new Entry(null, label)).cpu(nanos);
    }

    void frame() {
        long now = System.nanoTime();
        long last = frameStart;
        frameStart = now;
        if (last == 0 || now - last > STALE_NANOS) return;
        float ms = (now - last) / 1_000_000f;
        frameMs = frameMs <= 0f ? ms : frameMs * SMOOTHING + ms * (1f - SMOOTHING);
    }

    public float frameMs() {
        return frameMs;
    }

    public List<Entry> sorted() {
        long now = System.nanoTime();
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (now - e.updated < STALE_NANOS) out.add(e);
        }
        out.sort((a, b) -> Float.compare(b.costMs(), a.costMs()));
        return out;
    }

    public Map<RenderStage, List<Entry>> snapshot() {
        Map<RenderStage, List<Entry>> out = new LinkedHashMap<>();
        for (RenderStage s : RenderStage.values()) out.put(s, new ArrayList<>());
        for (Entry e : entries.values()) {
            if (e.stage != null) out.get(e.stage).add(e);
        }
        for (List<Entry> list : out.values()) {
            list.sort((a, b) -> a.label.compareTo(b.label));
        }
        return out;
    }
}
