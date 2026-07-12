package com.meekdev.amnetic.client.ssr;

public final class SsrSettings {

    private boolean enabled = false;
    private float intensity = 1.0f;
    private int maxSteps = 32;
    private float stride = 0.5f;
    private float maxDistance = 32f;
    private float thickness = 1.0f;
    private float edgeFade = 0.1f;
    private float reflectivity = 0.04f;
    private float resolution = 1.0f;
    private boolean temporal = true;
    private float feedback = 0.85f;

    SsrSettings() {}

    public SsrSettings enabled(boolean v) { this.enabled = v; return this; }
    public boolean isEnabled() { return enabled; }

    public SsrSettings intensity(float v) { this.intensity = Math.max(0f, v); return this; }
    public float intensity() { return intensity; }

    public SsrSettings maxSteps(int v) { this.maxSteps = Math.max(1, Math.min(256, v)); return this; }
    public int maxSteps() { return maxSteps; }

    public SsrSettings stride(float v) { this.stride = Math.max(0.01f, v); return this; }
    public float stride() { return stride; }

    public SsrSettings maxDistance(float v) { this.maxDistance = Math.max(0.1f, v); return this; }
    public float maxDistance() { return maxDistance; }

    public SsrSettings thickness(float v) { this.thickness = Math.max(0.01f, v); return this; }
    public float thickness() { return thickness; }

    public SsrSettings edgeFade(float v) { this.edgeFade = Math.max(0f, Math.min(0.5f, v)); return this; }
    public float edgeFade() { return edgeFade; }

    public SsrSettings reflectivity(float v) { this.reflectivity = Math.max(0f, Math.min(1f, v)); return this; }
    public float reflectivity() { return reflectivity; }

    public SsrSettings resolution(float v) { this.resolution = Math.max(0.1f, Math.min(1f, v)); return this; }
    public float resolution() { return resolution; }

    public SsrSettings temporal(boolean v) { this.temporal = v; return this; }
    public boolean temporal() { return temporal; }

    public SsrSettings feedback(float v) { this.feedback = Math.max(0f, Math.min(0.98f, v)); return this; }
    public float feedback() { return feedback; }
}
