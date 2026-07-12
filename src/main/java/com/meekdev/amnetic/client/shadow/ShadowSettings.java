package com.meekdev.amnetic.client.shadow;

public final class ShadowSettings {

    public static final int SPOT_GRID = 4;
    public static final int MAX_SPOT = SPOT_GRID * SPOT_GRID;
    public static final int MAX_POINT = 16;
    public static final int MAX_CASCADES = 4;

    private static final ShadowSettings INSTANCE = new ShadowSettings();

    private int resolution = 1024;
    private int maxSpotShadows = 8;
    private int maxPointShadows = 8;
    private float softness = 1.5f;
    private float bias = 0.0005f;
    private float normalBias = 0.05f;
    private float maxDistance = 64f;
    private float fadeStart = 0.8f;
    private boolean entityShadows = true;
    private boolean entityModels = true;
    private boolean pcss = true;
    private float lightSize = 2.5f;
    private int bakeBudget = 0;
    private int sunResolution = 2048;
    private int sunCascades = 4;
    private float sunDistance = 128f;
    private float sunSplitLambda = 0.7f;
    private float sunCasterExtension = 64f;
    private float sunBlockOccluderRadius = 48f;

    private ShadowSettings() {}

    public static ShadowSettings defaults() {
        return INSTANCE;
    }

    public ShadowSettings resolution(int px) {
        this.resolution = clampPow2(px, 256, 2048);
        return this;
    }

    public ShadowSettings maxSpotShadows(int n) {
        this.maxSpotShadows = Math.max(0, Math.min(MAX_SPOT, n));
        return this;
    }

    public ShadowSettings maxPointShadows(int n) {
        this.maxPointShadows = Math.max(0, Math.min(MAX_POINT, n));
        return this;
    }

    public ShadowSettings softness(float texels) {
        this.softness = Math.max(0f, Math.min(8f, texels));
        return this;
    }

    public ShadowSettings bias(float b) {
        this.bias = Math.max(0f, b);
        return this;
    }

    public ShadowSettings normalBias(float b) {
        this.normalBias = Math.max(0f, b);
        return this;
    }

    public ShadowSettings maxDistance(float blocks) {
        this.maxDistance = Math.max(8f, blocks);
        return this;
    }

    public ShadowSettings fadeStart(float fraction) {
        this.fadeStart = Math.max(0f, Math.min(1f, fraction));
        return this;
    }

    public ShadowSettings entityShadows(boolean v) {
        this.entityShadows = v;
        return this;
    }

    public ShadowSettings entityModels(boolean v) {
        this.entityModels = v;
        return this;
    }

    public ShadowSettings pcss(boolean v) {
        this.pcss = v;
        return this;
    }

    public ShadowSettings lightSize(float texels) {
        this.lightSize = Math.max(0.1f, Math.min(16f, texels));
        return this;
    }

    public ShadowSettings bakeBudget(int casters) {
        this.bakeBudget = Math.max(0, casters);
        return this;
    }

    public ShadowSettings sunResolution(int px) {
        this.sunResolution = clampPow2(px, 512, 4096);
        return this;
    }

    public ShadowSettings sunCascades(int n) {
        this.sunCascades = Math.max(1, Math.min(MAX_CASCADES, n));
        return this;
    }

    /** how far from the camera sun shadows reach (far edge of the loosest cascade), in blocks */
    public ShadowSettings sunDistance(float blocks) {
        this.sunDistance = Math.max(16f, blocks);
        return this;
    }

    /** practical-split blend: 0 = uniform splits, 1 = fully logarithmic (tight near the camera) */
    public ShadowSettings sunSplitLambda(float lambda) {
        this.sunSplitLambda = Math.max(0f, Math.min(1f, lambda));
        return this;
    }

    /** extra blocks each cascade reaches toward the sun so tall off-slice geometry still casts */
    public ShadowSettings sunCasterExtension(float blocks) {
        this.sunCasterExtension = Math.max(0f, blocks);
        return this;
    }

    /** radius around the camera within which vanilla blocks cast sun shadows, 0 disables block occluders
     * (custom models / instanced meshes still cast at any distance) */
    public ShadowSettings sunBlockOccluderRadius(float blocks) {
        this.sunBlockOccluderRadius = Math.max(0f, Math.min(96f, blocks));
        return this;
    }

    public int resolution() {
        return resolution;
    }

    public int pointFaceSize() {
        return Math.min(resolution, 1024);
    }

    public int maxSpotShadows() {
        return maxSpotShadows;
    }

    public int maxPointShadows() {
        return maxPointShadows;
    }

    public float softness() {
        return softness;
    }

    public float bias() {
        return bias;
    }

    public float normalBias() {
        return normalBias;
    }

    public float maxDistance() {
        return maxDistance;
    }

    public float fadeStart() {
        return fadeStart;
    }

    public float fadeStartDistance() {
        return maxDistance * fadeStart;
    }

    public boolean entityShadows() {
        return entityShadows;
    }

    public boolean entityModels() {
        return entityModels;
    }

    public boolean pcss() {
        return pcss;
    }

    public float lightSize() {
        return lightSize;
    }

    public int bakeBudget() {
        return bakeBudget;
    }

    public int sunResolution() {
        return sunResolution;
    }

    public int sunCascades() {
        return sunCascades;
    }

    public float sunDistance() {
        return sunDistance;
    }

    public float sunSplitLambda() {
        return sunSplitLambda;
    }

    public float sunCasterExtension() {
        return sunCasterExtension;
    }

    public float sunBlockOccluderRadius() {
        return sunBlockOccluderRadius;
    }

    private static int clampPow2(int v, int lo, int hi) {
        int p = Integer.highestOneBit(Math.max(lo, Math.min(hi, v)));
        return Math.max(lo, Math.min(hi, p));
    }
}