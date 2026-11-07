package com.meekdev.amnetic.client.model;

import net.minecraft.util.Mth;

public final class MaterialLook {
    float tintR = 1f;
    float tintG = 1f;
    float tintB = 1f;
    float opacity = 1f;
    float roughness = Float.NaN;
    float metallic = Float.NaN;
    float emR = Float.NaN;
    float emG;
    float emB;
    float emissiveStrength = 1f;
    boolean doubleSided;
    boolean dithered;
    TextureFilter filter;
    int baseColorGlId;
    int normalGlId;
    int roughnessGlId;
    int metallicGlId;
    int occlusionGlId;
    int emissiveGlId;
    float repeatU = 1f;
    float repeatV = 1f;
    float offsetU;
    float offsetV;
    float warp;

    public MaterialLook reset() {
        tintR = tintG = tintB = opacity = emissiveStrength = repeatU = repeatV = 1f;
        roughness = metallic = emR = Float.NaN;
        emG = emB = offsetU = offsetV = warp = 0f;
        doubleSided = dithered = false;
        filter = null;
        baseColorGlId = normalGlId = roughnessGlId = metallicGlId = occlusionGlId = emissiveGlId = 0;
        return this;
    }

    public MaterialLook tint(float r, float g, float b) {
        tintR *= r;
        tintG *= g;
        tintB *= b;
        return this;
    }

    public MaterialLook opacity(float value) {
        opacity *= value;
        return this;
    }

    public MaterialLook roughness(float value) {
        roughness = value;
        return this;
    }

    public MaterialLook metallic(float value) {
        metallic = value;
        return this;
    }

    public MaterialLook emissive(float r, float g, float b) {
        emR = r;
        emG = g;
        emB = b;
        return this;
    }

    public MaterialLook emissiveStrength(float value) {
        emissiveStrength *= value;
        return this;
    }

    public MaterialLook doubleSided(boolean value) {
        doubleSided |= value;
        return this;
    }

    public MaterialLook dithered(boolean value) {
        dithered |= value;
        return this;
    }

    public MaterialLook filter(TextureFilter value) {
        filter = value;
        return this;
    }

    public MaterialLook baseColorGlTexture(int glId) {
        baseColorGlId = glId;
        return this;
    }

    public MaterialLook normalGlTexture(int glId) {
        normalGlId = glId;
        return this;
    }

    public MaterialLook roughnessGlTexture(int glId) {
        roughnessGlId = glId;
        return this;
    }

    public MaterialLook metallicGlTexture(int glId) {
        metallicGlId = glId;
        return this;
    }

    public MaterialLook occlusionGlTexture(int glId) {
        occlusionGlId = glId;
        return this;
    }

    public MaterialLook emissiveGlTexture(int glId) {
        emissiveGlId = glId;
        return this;
    }

    public MaterialLook uv(float repeatU, float repeatV, float offsetU, float offsetV) {
        this.repeatU = repeatU;
        this.repeatV = repeatV;
        this.offsetU = offsetU;
        this.offsetV = offsetV;
        return this;
    }

    public MaterialLook warp(float amount) {
        warp = Mth.clamp(amount, 0f, 1f);
        return this;
    }

    public boolean emits() {
        return !Float.isNaN(emR) && emissiveStrength > 0f && (emR > 0f || emG > 0f || emB > 0f);
    }

    void onto(MaterialLook into) {
        into.tint(tintR, tintG, tintB).opacity(opacity).emissiveStrength(emissiveStrength).doubleSided(doubleSided).dithered(dithered);
        if (!Float.isNaN(roughness)) into.roughness = roughness;
        if (!Float.isNaN(metallic)) into.metallic = metallic;
        if (!Float.isNaN(emR)) into.emissive(emR, emG, emB);
        if (filter != null) into.filter = filter;
        if (baseColorGlId != 0) into.baseColorGlId = baseColorGlId;
        if (normalGlId != 0) into.normalGlId = normalGlId;
        if (roughnessGlId != 0) into.roughnessGlId = roughnessGlId;
        if (metallicGlId != 0) into.metallicGlId = metallicGlId;
        if (occlusionGlId != 0) into.occlusionGlId = occlusionGlId;
        if (emissiveGlId != 0) into.emissiveGlId = emissiveGlId;
        if (repeatU != 1f || repeatV != 1f || offsetU != 0f || offsetV != 0f) into.uv(repeatU, repeatV, offsetU, offsetV);
        if (warp > 0f) into.warp = warp;
    }

    public boolean sameAs(MaterialLook other) {
        return other == this || other != null
                && Float.compare(tintR, other.tintR) == 0 && Float.compare(tintG, other.tintG) == 0
                && Float.compare(tintB, other.tintB) == 0 && Float.compare(opacity, other.opacity) == 0
                && Float.compare(roughness, other.roughness) == 0 && Float.compare(metallic, other.metallic) == 0
                && Float.compare(emR, other.emR) == 0 && Float.compare(emG, other.emG) == 0
                && Float.compare(emB, other.emB) == 0 && Float.compare(emissiveStrength, other.emissiveStrength) == 0
                && doubleSided == other.doubleSided && dithered == other.dithered && filter == other.filter
                && baseColorGlId == other.baseColorGlId && normalGlId == other.normalGlId
                && roughnessGlId == other.roughnessGlId && metallicGlId == other.metallicGlId
                && occlusionGlId == other.occlusionGlId && emissiveGlId == other.emissiveGlId
                && Float.compare(repeatU, other.repeatU) == 0 && Float.compare(repeatV, other.repeatV) == 0
                && Float.compare(offsetU, other.offsetU) == 0 && Float.compare(offsetV, other.offsetV) == 0
                && Float.compare(warp, other.warp) == 0;
    }

    public int contentHash() {
        int h = Float.floatToIntBits(tintR);
        h = 31 * h + Float.floatToIntBits(tintG);
        h = 31 * h + Float.floatToIntBits(tintB);
        h = 31 * h + Float.floatToIntBits(opacity);
        h = 31 * h + Float.floatToIntBits(roughness);
        h = 31 * h + Float.floatToIntBits(metallic);
        h = 31 * h + Float.floatToIntBits(emR);
        h = 31 * h + Float.floatToIntBits(emG);
        h = 31 * h + Float.floatToIntBits(emB);
        h = 31 * h + Float.floatToIntBits(emissiveStrength);
        h = 31 * h + (doubleSided ? 1 : 0) + (dithered ? 2 : 0);
        h = 31 * h + (filter == null ? 0 : filter.ordinal() + 1);
        h = 31 * h + baseColorGlId;
        h = 31 * h + normalGlId;
        h = 31 * h + roughnessGlId;
        h = 31 * h + metallicGlId;
        h = 31 * h + occlusionGlId;
        h = 31 * h + emissiveGlId;
        h = 31 * h + Float.floatToIntBits(repeatU);
        h = 31 * h + Float.floatToIntBits(repeatV);
        h = 31 * h + Float.floatToIntBits(offsetU);
        h = 31 * h + Float.floatToIntBits(offsetV);
        return 31 * h + Float.floatToIntBits(warp);
    }

    public float warp() { return warp; }
    public float repeatU() { return repeatU; }
    public float repeatV() { return repeatV; }
    public float offsetU() { return offsetU; }
    public float offsetV() { return offsetV; }
    public float tintR() { return tintR; }
    public float tintG() { return tintG; }
    public float tintB() { return tintB; }
    public float opacity() { return opacity; }
    public float roughness() { return roughness; }
    public float metallic() { return metallic; }
    public float emissiveR() { return emR; }
    public float emissiveG() { return emG; }
    public float emissiveB() { return emB; }
    public float emissiveStrength() { return emissiveStrength; }
    public boolean doubleSided() { return doubleSided; }
    public boolean dithered() { return dithered; }
    public TextureFilter filter() { return filter; }
    public int baseColorGlTexture() { return baseColorGlId; }
    public int normalGlTexture() { return normalGlId; }
    public int roughnessGlTexture() { return roughnessGlId; }
    public int metallicGlTexture() { return metallicGlId; }
    public int occlusionGlTexture() { return occlusionGlId; }
    public int emissiveGlTexture() { return emissiveGlId; }
}
