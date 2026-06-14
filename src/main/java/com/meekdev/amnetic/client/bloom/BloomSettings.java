package com.meekdev.amnetic.client.bloom;

public final class BloomSettings {

    private boolean enabled = false;
    private boolean all = false;
    private boolean occlude = true;
    private float intensity = 1.0f;
    private int levels = 6;
    private float scale = 0.5f;

    BloomSettings() {}

    public BloomSettings enabled(boolean v) { this.enabled = v; return this; }

    public BloomSettings all(boolean v) { this.all = v; return this; }

    public BloomSettings occlude(boolean v) { this.occlude = v; return this; }

    public BloomSettings intensity(float v) { this.intensity = v; return this; }

    public BloomSettings levels(int v) { this.levels = Math.max(2, Math.min(8, v)); return this; }

    public BloomSettings scale(float v) { this.scale = Math.max(0.05f, Math.min(1.0f, v)); return this; }

    public boolean isEnabled() { return enabled; }
    public boolean isAll()     { return all; }
    public boolean isOcclude() { return occlude; }
    public float intensity()   { return intensity; }
    public int levels()        { return levels; }
    public float scale()       { return scale; }
}
