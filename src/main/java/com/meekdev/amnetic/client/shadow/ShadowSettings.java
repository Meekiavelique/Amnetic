package com.meekdev.amnetic.client.shadow;

import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;

public final class ShadowSettings {
    public static final int SPOT_GRID = 4;
    public static final int MAX_SPOT = SPOT_GRID * SPOT_GRID;
    public static final int MAX_POINT = 32;
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
    private Predicate<Entity> entityFilter = entity -> true;
    private boolean pcss = true;
    private float lightSize = 2.5f;
    private int bakeBudget = 0;
    private int sunResolution = 2048;
    private int sunCascades = 4;
    private float sunDistance = 128f;
    private float sunSplitLambda = 0.7f;
    private float sunCasterExtension = 64f;
    private float sunBlockOccluderRadius = 48f;
    private float budgetMs = 0f;
    private int version;

    private ShadowSettings() {}

    public static ShadowSettings defaults() {
        return INSTANCE;
    }

    public ShadowSettings resolution(int px) {
        int next = clampPow2(px, 256, 2048);
        if (next != resolution) {
            resolution = next;
            version++;
        }
        return this;
    }

    public ShadowSettings maxSpotShadows(int n) {
        int next = Math.max(0, Math.min(MAX_SPOT, n));
        if (next != maxSpotShadows) {
            maxSpotShadows = next;
            version++;
        }
        return this;
    }

    public ShadowSettings maxPointShadows(int n) {
        int next = Math.max(0, Math.min(MAX_POINT, n));
        if (next != maxPointShadows) {
            maxPointShadows = next;
            version++;
        }
        return this;
    }

    public ShadowSettings softness(float texels) {
        float next = Math.max(0f, Math.min(8f, texels));
        if (next != softness) {
            softness = next;
            version++;
        }
        return this;
    }

    public ShadowSettings bias(float b) {
        float next = Math.max(0f, b);
        if (next != bias) {
            bias = next;
            version++;
        }
        return this;
    }

    public ShadowSettings normalBias(float b) {
        float next = Math.max(0f, b);
        if (next != normalBias) {
            normalBias = next;
            version++;
        }
        return this;
    }

    public ShadowSettings maxDistance(float blocks) {
        float next = Math.max(8f, blocks);
        if (next != maxDistance) {
            maxDistance = next;
            version++;
        }
        return this;
    }

    public ShadowSettings fadeStart(float fraction) {
        float next = Math.max(0f, Math.min(1f, fraction));
        if (next != fadeStart) {
            fadeStart = next;
            version++;
        }
        return this;
    }

    public ShadowSettings entityShadows(boolean v) {
        if (v != entityShadows) {
            entityShadows = v;
            version++;
        }
        return this;
    }

    public ShadowSettings entityFilter(Predicate<Entity> filter) {
        if (filter != entityFilter) {
            entityFilter = filter;
            version++;
        }
        return this;
    }

    public ShadowSettings entityModels(boolean v) {
        if (v != entityModels) {
            entityModels = v;
            version++;
        }
        return this;
    }

    public ShadowSettings pcss(boolean v) {
        if (v != pcss) {
            pcss = v;
            version++;
        }
        return this;
    }

    public ShadowSettings lightSize(float texels) {
        float next = Math.max(0.1f, Math.min(16f, texels));
        if (next != lightSize) {
            lightSize = next;
            version++;
        }
        return this;
    }

    public ShadowSettings bakeBudget(int casters) {
        int next = Math.max(0, casters);
        if (next != bakeBudget) {
            bakeBudget = next;
            version++;
        }
        return this;
    }

    public ShadowSettings sunResolution(int px) {
        int next = clampPow2(px, 512, 4096);
        if (next != sunResolution) {
            sunResolution = next;
            version++;
        }
        return this;
    }

    public ShadowSettings sunCascades(int n) {
        int next = Math.max(1, Math.min(MAX_CASCADES, n));
        if (next != sunCascades) {
            sunCascades = next;
            version++;
        }
        return this;
    }

    public ShadowSettings sunDistance(float blocks) {
        float next = Math.max(16f, blocks);
        if (next != sunDistance) {
            sunDistance = next;
            version++;
        }
        return this;
    }

    public ShadowSettings sunSplitLambda(float lambda) {
        float next = Math.max(0f, Math.min(1f, lambda));
        if (next != sunSplitLambda) {
            sunSplitLambda = next;
            version++;
        }
        return this;
    }

    public ShadowSettings sunCasterExtension(float blocks) {
        float next = Math.max(0f, blocks);
        if (next != sunCasterExtension) {
            sunCasterExtension = next;
            version++;
        }
        return this;
    }

    public ShadowSettings sunBlockOccluderRadius(float blocks) {
        float next = Math.max(0f, Math.min(96f, blocks));
        if (next != sunBlockOccluderRadius) {
            sunBlockOccluderRadius = next;
            version++;
        }
        return this;
    }

    public ShadowSettings budgetMs(float ms) {
        float next = Math.max(0f, ms);
        if (next != budgetMs) {
            budgetMs = next;
            version++;
        }
        return this;
    }

    public float budgetMs() {
        return budgetMs;
    }

    public int version() {
        return version;
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

    public boolean castsShadow(Entity entity) {
        return entityFilter.test(entity);
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