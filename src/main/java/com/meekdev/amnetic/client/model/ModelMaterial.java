package com.meekdev.amnetic.client.model;

import com.meekdev.amnetic.client.model.internal.ModelIR;
import net.minecraft.resources.Identifier;

public final class ModelMaterial {

    private final ModelIR.Material mat;

    public ModelMaterial(ModelIR.Material mat) {
        this.mat = mat;
    }

    public String name() { return mat.name; }

    public ModelMaterial setBaseColor(float r, float g, float b) {
        mat.baseR = r; mat.baseG = g; mat.baseB = b; return this;
    }

    public ModelMaterial setBaseColor(float r, float g, float b, float a) {
        mat.baseR = r; mat.baseG = g; mat.baseB = b; mat.baseA = a; return this;
    }

    public ModelMaterial setOpacity(float a) { mat.baseA = a; return this; }

    public ModelMaterial setMetallic(float metallic) {
        mat.metallic = clamp01(metallic); return this;
    }

    public ModelMaterial setRoughness(float roughness) {
        mat.roughness = clamp01(roughness); return this;
    }

    public ModelMaterial setEmissive(float r, float g, float b) {
        mat.emR = r; mat.emG = g; mat.emB = b; return this;
    }

    public ModelMaterial setBaseColorTexture(Identifier texture) {
        mat.baseColorTexture = texture;
        mat.baseColorImageBytes = null;   // identifier takes precedence over any embedded image
        return this;
    }

    public ModelMaterial clearBaseColorTexture() {
        mat.baseColorTexture = null;
        mat.baseColorImageBytes = null;
        return this;
    }

    public boolean hasBaseColorTexture() {
        return mat.baseColorTexture != null || mat.baseColorImageBytes != null;
    }

    public float baseR() { return mat.baseR; }
    public float baseG() { return mat.baseG; }
    public float baseB() { return mat.baseB; }
    public float baseA() { return mat.baseA; }
    public float metallic() { return mat.metallic; }
    public float roughness() { return mat.roughness; }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
