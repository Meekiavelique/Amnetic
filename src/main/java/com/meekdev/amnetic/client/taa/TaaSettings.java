package com.meekdev.amnetic.client.taa;

import com.meekdev.amnetic.platform.Platform;

public final class TaaSettings {
    private static final boolean ALLOWED = !Platform.isModLoaded("asterion");
    private boolean enabled = false;
    private float feedback = 0.94f;
    private float sharpness = 0.4f;
    private float clipTightness = 1.0f;

    TaaSettings() {}

    public TaaSettings enabled(boolean v) { this.enabled = v && ALLOWED; return this; }
    public boolean isEnabled() { return enabled && ALLOWED; }

    public TaaSettings feedback(float v) { this.feedback = Math.max(0f, Math.min(0.98f, v)); return this; }
    public float feedback() { return feedback; }

    public TaaSettings sharpness(float v) { this.sharpness = Math.max(0f, Math.min(1f, v)); return this; }
    public float sharpness() { return sharpness; }

    public TaaSettings clipTightness(float v) { this.clipTightness = Math.max(0.2f, Math.min(3f, v)); return this; }
    public float clipTightness() { return clipTightness; }
}
