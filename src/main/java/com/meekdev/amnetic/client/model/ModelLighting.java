package com.meekdev.amnetic.client.model;

/**
 * tunable lighting/grading for the glTF model PBR shader, e.g.
 * {@code ModelLighting.INSTANCE.sunIntensity(1.2f).exposure(1.1f);}
 */
public final class ModelLighting {

    public static final ModelLighting INSTANCE = new ModelLighting();

    private float sunX = 0.35f, sunY = 0.85f, sunZ = 0.40f;
    private float sunR = 1.0f, sunG = 0.97f, sunB = 0.92f;
    private float sunIntensity = 1.0f;
    private float ambientStrength = 1.0f;
    private float envIntensity = 1.0f;
    private float exposure = 1.0f;
    private boolean tonemap = true;

    private ModelLighting() {}

    public ModelLighting sunDirection(float x, float y, float z) { sunX = x; sunY = y; sunZ = z; return this; }
    public ModelLighting sunColor(float r, float g, float b) { sunR = r; sunG = g; sunB = b; return this; }
    public ModelLighting sunIntensity(float v) { sunIntensity = Math.max(0f, v); return this; }
    public ModelLighting ambientStrength(float v) { ambientStrength = Math.max(0f, v); return this; }
    public ModelLighting envIntensity(float v) { envIntensity = Math.max(0f, v); return this; }
    public ModelLighting exposure(float v) { exposure = Math.max(0.01f, v); return this; }
    public ModelLighting tonemap(boolean v) { tonemap = v; return this; }

    public float sunX() { return sunX; }
    public float sunY() { return sunY; }
    public float sunZ() { return sunZ; }
    public float sunR() { return sunR; }
    public float sunG() { return sunG; }
    public float sunB() { return sunB; }
    public float sunIntensity() { return sunIntensity; }
    public float ambientStrength() { return ambientStrength; }
    public float envIntensity() { return envIntensity; }
    public float exposure() { return exposure; }
    public boolean tonemap() { return tonemap; }
}
