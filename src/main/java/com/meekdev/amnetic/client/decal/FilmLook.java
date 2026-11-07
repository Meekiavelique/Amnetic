package com.meekdev.amnetic.client.decal;

import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

public final class FilmLook {
    private static final int PALETTE_COLORS = 8;
    final Vector3f absorption = new Vector3f(30f, 170f, 210f);
    final Vector3f agedAbsorption = new Vector3f(80f, 190f, 220f);
    final Vector3f stainTint = new Vector3f(0.42f, 0.2f, 0.15f);
    final Vector3f scatter = new Vector3f(0.10f, 0.01f, 0.005f);
    float maxThickness = 0.05f;
    float edgeThickness = 0.0015f;
    float edgeSoftness = 0.0006f;
    float edgeNoise = 0.35f;
    float edgeNoiseScale = 9f;
    float stainThreshold = 0.06f;
    float ring = 1.4f;
    float normalStrength = 18f;
    float rim = 2f;
    float flowSpeed = 1.2f;
    float rippleStrength = 0.25f;
    float parallax = 1f;
    float specular = 1f;
    float wetRoughness = 0.06f;
    float dryRoughness = 0.85f;
    float reflectance = 0.02f;
    float screenReflect = 1f;
    float screenReflectDistance = 24f;
    float rain;
    float rainScale = 2.4f;
    float rainStrength = 0.35f;
    boolean pixelArt;
    float texelsPerUnit = 16f;
    final float[] palette = new float[PALETTE_COLORS * 3];
    float jitter = 1f;
    int outline = 1;
    boolean core;
    float highlights;
    Identifier patternWet;
    Identifier patternDry;

    public FilmLook absorption(float r, float g, float b) {
        absorption.set(r, g, b);
        return this;
    }

    public FilmLook agedAbsorption(float r, float g, float b) {
        agedAbsorption.set(r, g, b);
        return this;
    }

    public FilmLook stainTint(float r, float g, float b) {
        stainTint.set(r, g, b);
        return this;
    }

    public FilmLook scatter(float r, float g, float b) {
        scatter.set(r, g, b);
        return this;
    }

    public FilmLook maxThickness(float v) {
        maxThickness = Math.max(1e-4f, v);
        return this;
    }

    public FilmLook edge(float thickness, float softness) {
        edgeThickness = Math.max(0f, thickness);
        edgeSoftness = Math.max(1e-6f, softness);
        return this;
    }

    public FilmLook edgeNoise(float amount, float scale) {
        edgeNoise = Math.max(0f, amount);
        edgeNoiseScale = Math.max(0.01f, scale);
        return this;
    }

    public FilmLook stainThreshold(float v) {
        stainThreshold = Math.max(0f, v);
        return this;
    }

    public FilmLook ring(float v) {
        ring = Math.max(0f, v);
        return this;
    }

    public FilmLook normals(float strength, float rim) {
        normalStrength = Math.max(0f, strength);
        this.rim = Math.max(0f, rim);
        return this;
    }

    public FilmLook flow(float speed, float ripple) {
        flowSpeed = speed;
        rippleStrength = Math.max(0f, ripple);
        return this;
    }

    public FilmLook parallax(float v) {
        parallax = Math.max(0f, v);
        return this;
    }

    public FilmLook specular(float v) {
        specular = Math.max(0f, v);
        return this;
    }

    public FilmLook roughness(float wet, float dry) {
        wetRoughness = clamp01(wet);
        dryRoughness = clamp01(dry);
        return this;
    }

    public FilmLook reflectance(float v) {
        reflectance = clamp01(v);
        return this;
    }

    public FilmLook screenReflections(float strength, float distance) {
        screenReflect = clamp01(strength);
        screenReflectDistance = Math.max(1f, distance);
        return this;
    }

    public FilmLook rain(float v) {
        rain = clamp01(v);
        return this;
    }

    public FilmLook rainRipples(float scale, float strength) {
        rainScale = Math.max(0.1f, scale);
        rainStrength = Math.max(0f, strength);
        return this;
    }

    public FilmLook pixelArt(boolean v) {
        pixelArt = v;
        return this;
    }

    public FilmLook texelsPerUnit(float v) {
        texelsPerUnit = Math.max(1f, v);
        return this;
    }

    public FilmLook pixelStyle(float jitter, int outline, boolean core, float highlights) {
        this.jitter = Math.max(0f, Math.min(1f, jitter));
        this.outline = Math.max(0, Math.min(2, outline));
        this.core = core;
        this.highlights = Math.max(0f, Math.min(1f, highlights));
        return this;
    }

    public FilmLook pattern(Identifier wet, Identifier dry) {
        patternWet = wet;
        patternDry = dry;
        return this;
    }

    public FilmLook palette(int... rgb) {
        for (int i = 0; i < PALETTE_COLORS && i < rgb.length; i++) {
            palette[i * 3] = ((rgb[i] >> 16) & 255) / 255f;
            palette[i * 3 + 1] = ((rgb[i] >> 8) & 255) / 255f;
            palette[i * 3 + 2] = (rgb[i] & 255) / 255f;
        }
        return this;
    }

    public FilmLook copy() {
        FilmLook c = new FilmLook();
        c.absorption.set(absorption);
        c.agedAbsorption.set(agedAbsorption);
        c.stainTint.set(stainTint);
        c.scatter.set(scatter);
        c.maxThickness = maxThickness;
        c.edgeThickness = edgeThickness;
        c.edgeSoftness = edgeSoftness;
        c.edgeNoise = edgeNoise;
        c.edgeNoiseScale = edgeNoiseScale;
        c.stainThreshold = stainThreshold;
        c.ring = ring;
        c.normalStrength = normalStrength;
        c.rim = rim;
        c.flowSpeed = flowSpeed;
        c.rippleStrength = rippleStrength;
        c.parallax = parallax;
        c.specular = specular;
        c.wetRoughness = wetRoughness;
        c.dryRoughness = dryRoughness;
        c.reflectance = reflectance;
        c.screenReflect = screenReflect;
        c.screenReflectDistance = screenReflectDistance;
        c.rain = rain;
        c.rainScale = rainScale;
        c.rainStrength = rainStrength;
        c.pixelArt = pixelArt;
        c.texelsPerUnit = texelsPerUnit;
        c.jitter = jitter;
        c.outline = outline;
        c.core = core;
        c.highlights = highlights;
        c.patternWet = patternWet;
        c.patternDry = patternDry;
        System.arraycopy(palette, 0, c.palette, 0, palette.length);
        return c;
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    public Vector3f absorption() { return absorption; }
    public Vector3f agedAbsorption() { return agedAbsorption; }
    public Vector3f stainTint() { return stainTint; }
    public Vector3f scatter() { return scatter; }
    public float maxThickness() { return maxThickness; }
    public float edgeThickness() { return edgeThickness; }
    public float edgeSoftness() { return edgeSoftness; }
    public float edgeNoise() { return edgeNoise; }
    public float edgeNoiseScale() { return edgeNoiseScale; }
    public float stainThreshold() { return stainThreshold; }
    public float ring() { return ring; }
    public float normalStrength() { return normalStrength; }
    public float rim() { return rim; }
    public float flowSpeed() { return flowSpeed; }
    public float rippleStrength() { return rippleStrength; }
    public float parallax() { return parallax; }
    public float specular() { return specular; }
    public float wetRoughness() { return wetRoughness; }
    public float dryRoughness() { return dryRoughness; }
    public float reflectance() { return reflectance; }
    public float screenReflect() { return screenReflect; }
    public float screenReflectDistance() { return screenReflectDistance; }
    public float rain() { return rain; }
    public float rainScale() { return rainScale; }
    public float rainStrength() { return rainStrength; }
    public boolean pixelArt() { return pixelArt; }
    public float texelsPerUnit() { return texelsPerUnit; }
}
