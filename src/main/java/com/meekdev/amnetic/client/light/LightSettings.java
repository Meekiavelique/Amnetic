package com.meekdev.amnetic.client.light;


public final class LightSettings {

    private static final LightSettings INSTANCE = new LightSettings();

    private int maxLights = 256;

    // Culling
    private boolean frustumCull = true;        // skip lights whose sphere is off-screen
    private float lodFadeStart = 64f;          // blocks past a light's range where it starts fading out
    private float lodFadeEnd = 128f;           // blocks past range where it's fully culled

    private boolean volumetric = false;
    private int volumetricSteps = 24;          // march steps (0 disables)
    private float volumetricStrength = 0.4f;   // scattering amount (averaged, then clamped in-shader)

    private LightSettings() {}

    public static LightSettings defaults() { return INSTANCE; }

    public LightSettings maxLights(int max) { this.maxLights = Math.max(1, max); return this; }
    public LightSettings frustumCull(boolean on) { this.frustumCull = on; return this; }
    public LightSettings lodFade(float startBlocks, float endBlocks) {
        this.lodFadeStart = Math.max(0f, startBlocks);
        this.lodFadeEnd = Math.max(this.lodFadeStart + 1f, endBlocks);
        return this;
    }
    public LightSettings volumetric(boolean on) { this.volumetric = on; return this; }
    public LightSettings volumetricSteps(int steps) { this.volumetricSteps = Math.max(0, Math.min(64, steps)); return this; }
    public LightSettings volumetricStrength(float s) { this.volumetricStrength = Math.max(0f, s); return this; }

    public int maxLights() { return maxLights; }
    public boolean frustumCull() { return frustumCull; }
    public float lodFadeStart() { return lodFadeStart; }
    public float lodFadeEnd() { return lodFadeEnd; }
    public boolean volumetric() { return volumetric; }
    public int volumetricSteps() { return volumetric ? volumetricSteps : 0; }
    public float volumetricStrength() { return volumetricStrength; }
}
