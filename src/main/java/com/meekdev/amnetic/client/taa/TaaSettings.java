package com.meekdev.amnetic.client.taa;

public final class TaaSettings {

    private boolean enabled = true;
    private float feedback = 0.9f;
    private float sharpness = 0.4f;

    TaaSettings() {}

    public TaaSettings enabled(boolean v) { this.enabled = v; return this; }
    public boolean isEnabled() { return enabled; }

    public TaaSettings feedback(float v) { this.feedback = Math.max(0f, Math.min(0.98f, v)); return this; }
    public float feedback() { return feedback; }

    public TaaSettings sharpness(float v) { this.sharpness = Math.max(0f, Math.min(1f, v)); return this; }
    public float sharpness() { return sharpness; }
}
