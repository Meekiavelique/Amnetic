package com.meekdev.amnetic.client.taa;

public final class TaaSettings {

    private boolean enabled = true;
    private float feedback = 0.9f;
    private float sharpness = 0.4f;
    private float clipTightness = 1.0f;

    TaaSettings() {}

    public TaaSettings enabled(boolean v) { this.enabled = v; return this; }
    public boolean isEnabled() { return enabled; }

    public TaaSettings feedback(float v) { this.feedback = Math.max(0f, Math.min(0.98f, v)); return this; }
    public float feedback() { return feedback; }

    public TaaSettings sharpness(float v) { this.sharpness = Math.max(0f, Math.min(1f, v)); return this; }
    public float sharpness() { return sharpness; }

    // how many standard deviations of the 3x3 neighbourhood the history is allowed to sit outside
    // before it gets clipped back in. reprojection is camera only, so an object that moves while the
    // camera holds still reprojects to itself and this clip is the only thing rejecting its stale
    // history. lower it to kill trailing on moving models, at the cost of some accumulation
    public TaaSettings clipTightness(float v) { this.clipTightness = Math.max(0.2f, Math.min(3f, v)); return this; }
    public float clipTightness() { return clipTightness; }
}
