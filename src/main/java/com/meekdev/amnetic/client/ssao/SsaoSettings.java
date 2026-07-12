package com.meekdev.amnetic.client.ssao;

public final class SsaoSettings {

    private boolean enabled;
    private float radius = 0.8f;
    private float intensity = 1.2f;
    private float bias = 0.025f;
    private float power = 1.5f;
    private float scale = 0.5f;
    private boolean temporal = true;
    private float feedback = 0.9f;

    public boolean isEnabled() {
        return enabled;
    }

    public SsaoSettings enabled(boolean v) {
        this.enabled = v;
        return this;
    }

    public SsaoSettings radius(float v) {
        this.radius = Math.max(0.05f, v);
        return this;
    }

    public SsaoSettings intensity(float v) {
        this.intensity = Math.max(0f, v);
        return this;
    }

    public SsaoSettings bias(float v) {
        this.bias = Math.max(0f, v);
        return this;
    }

    public SsaoSettings power(float v) {
        this.power = Math.max(0.1f, v);
        return this;
    }

    public SsaoSettings scale(float v) {
        this.scale = Math.max(0.25f, Math.min(1f, v));
        return this;
    }

    public SsaoSettings temporal(boolean v) {
        this.temporal = v;
        return this;
    }

    public SsaoSettings feedback(float v) {
        this.feedback = Math.max(0f, Math.min(0.95f, v));
        return this;
    }

    public boolean temporal() {
        return temporal;
    }

    public float feedback() {
        return feedback;
    }

    public float radius() {
        return radius;
    }

    public float intensity() {
        return intensity;
    }

    public float bias() {
        return bias;
    }

    public float power() {
        return power;
    }

    public float scale() {
        return scale;
    }
}