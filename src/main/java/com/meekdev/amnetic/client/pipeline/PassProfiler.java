package com.meekdev.amnetic.client.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

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

    private static final class Stamp {
        final int[] queries = new int[8];
        final boolean[] pending = new boolean[4];
        int write, read;
        long cpuStart;
        boolean open;

        Stamp() {
            GL15.glGenQueries(queries);
        }
    }

    private final Map<String, Stamp> stamps = new ConcurrentHashMap<>();

    public void begin(String label) {
        if (!active()) return;
        Stamp s = stamps.computeIfAbsent(label, k -> new Stamp());
        Entry e = entries.computeIfAbsent(label, k -> new Entry(null, label));
        while (s.pending[s.read] && GL15.glGetQueryObjecti(s.queries[s.read * 2 + 1], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
            long a = GL33.glGetQueryObjecti64(s.queries[s.read * 2], GL15.GL_QUERY_RESULT);
            long b = GL33.glGetQueryObjecti64(s.queries[s.read * 2 + 1], GL15.GL_QUERY_RESULT);
            e.gpu((b - a) / 1_000_000f);
            s.pending[s.read] = false;
            s.read = (s.read + 1) % 4;
        }
        s.open = !s.pending[s.write];
        if (!s.open) return;
        GL33.glQueryCounter(s.queries[s.write * 2], GL33.GL_TIMESTAMP);
        s.cpuStart = System.nanoTime();
    }

    public void end(String label) {
        Stamp s = stamps.get(label);
        if (s == null || !s.open) return;
        GL33.glQueryCounter(s.queries[s.write * 2 + 1], GL33.GL_TIMESTAMP);
        s.pending[s.write] = true;
        s.write = (s.write + 1) % 4;
        s.open = false;
        entries.get(label).cpu(System.nanoTime() - s.cpuStart);
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
