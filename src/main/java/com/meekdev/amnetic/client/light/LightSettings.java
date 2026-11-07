package com.meekdev.amnetic.client.light;

import net.minecraft.resources.Identifier;
import java.util.Locale;

public final class LightSettings {
    private static final LightSettings INSTANCE = new LightSettings();

    private int maxLights = 256;

    private boolean frustumCull = true;
    private boolean lightVolumes = true;
    private float surfaceScale = initialSurfaceScale();
    private float lodFadeStart = 64f;
    private float lodFadeEnd = 128f;

    private boolean volumetric = false;
    private int volumetricSteps = 16;
    private float volumetricStrength = 1.0f;
    private float volumetricDensity = 0.4f;
    private float volumetricAniso = 0.6f;
    private boolean volumetricShadows = true;
    private float volumetricScale = 0.5f;
    private boolean volumetricTemporal = true;

    private boolean contactShadows = false;
    private float sceneAmbient = 1f;
    private int contactSteps = 12;
    private float contactDistance = 0.5f;
    private float contactThickness = 0.15f;

    private Identifier cookieTexture;

    private LightSettings() {}

    private static float initialSurfaceScale() {
        return switch (System.getProperty("amnetic.lightingQuality", "balanced").toLowerCase(Locale.ROOT)) {
            case "high" -> 1f;
            case "performance" -> 0.5f;
            default -> 0.75f;
        };
    }

    public static LightSettings defaults() {
        return INSTANCE;
    }

    public LightSettings surfaceScale(float scale) {
        if (!Float.isFinite(scale)) throw new IllegalArgumentException("Lighting scale must be finite");
        surfaceScale = Math.max(0.5f, Math.min(1f, scale));
        return this;
    }

    public float surfaceScale() { return surfaceScale; }

    public LightSettings maxLights(int max) {
        this.maxLights = Math.max(1, max);
        return this;
    }

    public LightSettings frustumCull(boolean on) {
        this.frustumCull = on;
        return this;
    }

    public LightSettings lodFade(float startBlocks, float endBlocks) {
        this.lodFadeStart = Math.max(0f, startBlocks);
        this.lodFadeEnd = Math.max(this.lodFadeStart + 1f, endBlocks);
        return this;
    }

    public LightSettings volumetric(boolean on) {
        this.volumetric = on;
        return this;
    }

    public LightSettings volumetricSteps(int steps) {
        this.volumetricSteps = Math.max(0, Math.min(64, steps));
        return this;
    }

    public LightSettings volumetricStrength(float s) {
        this.volumetricStrength = Math.max(0f, s);
        return this;
    }

    public LightSettings volumetricScale(float s) {
        this.volumetricScale = Math.max(0.25f, Math.min(1f, s));
        return this;
    }

    public LightSettings volumetricTemporal(boolean on) {
        this.volumetricTemporal = on;
        return this;
    }

    public LightSettings volumetricDensity(float d) {
        this.volumetricDensity = Math.max(0f, d);
        return this;
    }

    public LightSettings volumetricAniso(float g) {
        this.volumetricAniso = Math.max(0f, Math.min(0.95f, g));
        return this;
    }

    public LightSettings volumetricShadows(boolean on) {
        this.volumetricShadows = on;
        return this;
    }

    public LightSettings contactShadows(boolean on) {
        this.contactShadows = on;
        return this;
    }

    public LightSettings sceneAmbient(float ambient) {
        this.sceneAmbient = Math.max(0f, Math.min(1f, ambient));
        return this;
    }

    public LightSettings contactSteps(int steps) {
        this.contactSteps = Math.max(0, Math.min(64, steps));
        return this;
    }

    public LightSettings contactDistance(float blocks) {
        this.contactDistance = Math.max(0.01f, blocks);
        return this;
    }

    public LightSettings contactThickness(float blocks) {
        this.contactThickness = Math.max(0.01f, blocks);
        return this;
    }

    public LightSettings cookieTexture(Identifier id) {
        this.cookieTexture = id;
        return this;
    }

    public int maxLights() {
        return maxLights;
    }

    public boolean lightVolumes() {
        return lightVolumes;
    }

    public LightSettings lightVolumes(boolean v) {
        lightVolumes = v;
        return this;
    }

    public boolean frustumCull() {
        return frustumCull;
    }

    public float lodFadeStart() {
        return lodFadeStart;
    }

    public float lodFadeEnd() {
        return lodFadeEnd;
    }

    public boolean volumetric() {
        return volumetric;
    }

    public int volumetricSteps() {
        return volumetric ? volumetricSteps : 0;
    }

    public float volumetricStrength() {
        return volumetricStrength;
    }

    public float volumetricDensity() {
        return volumetricDensity;
    }

    public float volumetricAniso() {
        return volumetricAniso;
    }

    public boolean volumetricShadows() {
        return volumetricShadows;
    }

    public float volumetricScale() {
        return volumetricScale;
    }

    public boolean volumetricTemporal() {
        return volumetricTemporal;
    }

    public boolean contactShadows() {
        return contactShadows;
    }

    public float sceneAmbient() {
        return sceneAmbient;
    }

    public int contactSteps() {
        return contactShadows ? contactSteps : 0;
    }

    public float contactDistance() {
        return contactDistance;
    }

    public float contactThickness() {
        return contactThickness;
    }

    public Identifier cookieTexture() {
        return cookieTexture;
    }
}
