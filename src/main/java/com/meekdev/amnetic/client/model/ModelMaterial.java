package com.meekdev.amnetic.client.model;

import com.meekdev.amnetic.client.material.ShadingModel;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import net.minecraft.resources.Identifier;

public final class ModelMaterial {

    private final ModelIR.Material mat;

    public ModelMaterial(ModelIR.Material mat) {
        this.mat = mat;
    }

    public String name() {
        return mat.name;
    }

    public ModelMaterial setBaseColor(float r, float g, float b) {
        mat.baseR = r;
        mat.baseG = g;
        mat.baseB = b;
        return this;
    }

    public ModelMaterial setBaseColor(float r, float g, float b, float a) {
        mat.baseR = r;
        mat.baseG = g;
        mat.baseB = b;
        mat.baseA = a;
        return this;
    }

    public ModelMaterial setOpacity(float a) {
        mat.baseA = a;
        if (a < 0.999f) {
            mat.blend = true;
        }
        return this;
    }

    public ModelMaterial setMetallic(float metallic) {
        mat.metallic = clamp01(metallic);
        return this;
    }

    public ModelMaterial setRoughness(float roughness) {
        mat.roughness = clamp01(roughness);
        return this;
    }

    public ModelMaterial setEmissive(float r, float g, float b) {
        mat.emR = r;
        mat.emG = g;
        mat.emB = b;
        return this;
    }

    public ModelMaterial setAlphaCutoff(float cutoff) {
        mat.alphaCutoff = clamp01(cutoff);
        return this;
    }

    public ModelMaterial setBlend(boolean blend) {
        mat.blend = blend;
        return this;
    }

    // KHR transmission factor, 0 disables the glass path (renders as an opaque env-tinted sheet)
    public ModelMaterial setTransmission(float v) {
        mat.transmission = v;
        return this;
    }

    public ModelMaterial setDoubleSided(boolean doubleSided) {
        mat.doubleSided = doubleSided;
        return this;
    }

    public ModelMaterial setShadingModel(ShadingModel model) {
        mat.shadingModelId = model.id();
        return this;
    }

    public int shadingModelId() {
        return mat.shadingModelId;
    }

    public ModelMaterial setBaseColorTexture(Identifier texture) {
        mat.baseColorTexture = texture;
        mat.baseColorImageBytes = null;
        return this;
    }

    public ModelMaterial setNormalTexture(Identifier texture) {
        mat.normalTexture = texture;
        mat.normalImageBytes = null;
        return this;
    }

    public ModelMaterial setMetallicRoughnessTexture(Identifier texture) {
        mat.ormTexture = texture;
        mat.ormImageBytes = null;
        return this;
    }

    public ModelMaterial setBaseColorGlTexture(int glId) {
        return setBaseColorGlTexture(glId, TextureFilter.LINEAR);
    }

    public ModelMaterial setBaseColorGlTexture(int glId, TextureFilter filter) {
        mat.baseColorGlId = glId;
        mat.baseColorFilter = filter;
        return this;
    }

    /** binds a caller-owned live GL texture as the emissive map (e.g. a video screen). caller keeps
     *  ownership and updates it with glTexSubImage2D, pass 0 to detach */
    public ModelMaterial setEmissiveGlTexture(int glId) {
        mat.emissiveGlId = glId;
        mat.emissiveTexture = null;
        mat.emissiveImageBytes = null;
        return this;
    }

    public ModelMaterial setEmissiveTexture(Identifier texture) {
        mat.emissiveTexture = texture;
        mat.emissiveImageBytes = null;
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

    public float baseR() {
        return mat.baseR;
    }

    public float baseG() {
        return mat.baseG;
    }

    public float baseB() {
        return mat.baseB;
    }

    public float baseA() {
        return mat.baseA;
    }

    public float metallic() {
        return mat.metallic;
    }

    public float roughness() {
        return mat.roughness;
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        if (v > 1f) {
            return 1f;
        }
        return v;
    }
}
