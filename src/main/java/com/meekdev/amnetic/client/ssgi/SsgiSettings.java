package com.meekdev.amnetic.client.ssgi;

public final class SsgiSettings {

    private boolean enabled;
    private float radius = 2.0f;
    private float intensity = 1.0f;
    private float scale = 0.75f;
    private float historyBlend = 0.9f;
    private int maxHistoryFrames = 16;

    public boolean isEnabled() { return enabled; }
    public SsgiSettings enabled(boolean v) { this.enabled = v; return this; }
    public SsgiSettings radius(float v) { this.radius = Math.max(0.1f, v); return this; }
    public SsgiSettings intensity(float v) { this.intensity = Math.max(0f, v); return this; }
    public SsgiSettings scale(float v) { this.scale = Math.max(0.25f, Math.min(1f, v)); return this; }

    /** exponential moving-average weight given to reprojected history each frame (0 = no temporal
     *  accumulation, close to 1 = very slow to update but very stable) */
    public SsgiSettings historyBlend(float v) { this.historyBlend = Math.max(0f, Math.min(0.98f, v)); return this; }

    /** caps how many frames the temporal ramp-up takes to reach historyBlend() after a
     *  disocclusion/reset, so the first frames after a cut don't look under-converged forever */
    public SsgiSettings maxHistoryFrames(int v) { this.maxHistoryFrames = Math.max(1, v); return this; }

    public float radius() { return radius; }
    public float intensity() { return intensity; }
    public float scale() { return scale; }
    public float historyBlend() { return historyBlend; }
    public int maxHistoryFrames() { return maxHistoryFrames; }
}
