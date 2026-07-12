package com.meekdev.amnetic.client.grade;

import net.minecraft.resources.Identifier;

public final class ColorGradeSettings {

    private boolean enabled = false;
    private float exposure = 1.0f;
    private float contrast = 1.0f;
    private float saturation = 1.0f;
    private float brightness = 0.0f;
    private float temperature = 0.0f;
    private float tint = 0.0f;
    private float gamma = 1.0f;
    private Identifier lut = null;
    private int lutSize = 16;
    private float lutIntensity = 1.0f;

    ColorGradeSettings() {}

    public ColorGradeSettings enabled(boolean v) { this.enabled = v; return this; }
    public boolean isEnabled() { return enabled; }

    public ColorGradeSettings exposure(float v) { this.exposure = Math.max(0f, v); return this; }
    public float exposure() { return exposure; }

    public ColorGradeSettings contrast(float v) { this.contrast = Math.max(0f, v); return this; }
    public float contrast() { return contrast; }

    public ColorGradeSettings saturation(float v) { this.saturation = Math.max(0f, v); return this; }
    public float saturation() { return saturation; }

    public ColorGradeSettings brightness(float v) { this.brightness = v; return this; }
    public float brightness() { return brightness; }

    public ColorGradeSettings temperature(float v) { this.temperature = Math.max(-1f, Math.min(1f, v)); return this; }
    public float temperature() { return temperature; }

    public ColorGradeSettings tint(float v) { this.tint = Math.max(-1f, Math.min(1f, v)); return this; }
    public float tint() { return tint; }

    public ColorGradeSettings gamma(float v) { this.gamma = Math.max(0.01f, v); return this; }
    public float gamma() { return gamma; }

    public ColorGradeSettings lut(Identifier id) { this.lut = id; return this; }
    public Identifier lut() { return lut; }

    public ColorGradeSettings lutSize(int v) { this.lutSize = Math.max(2, v); return this; }
    public int lutSize() { return lutSize; }

    public ColorGradeSettings lutIntensity(float v) { this.lutIntensity = Math.max(0f, Math.min(1f, v)); return this; }
    public float lutIntensity() { return lutIntensity; }
}
