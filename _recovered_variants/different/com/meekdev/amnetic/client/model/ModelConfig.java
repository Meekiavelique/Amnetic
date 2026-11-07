package com.meekdev.amnetic.client.model;

import com.meekdev.amnetic.client.instanced.RenderState;
import net.minecraft.resources.ResourceLocation;

public final class ModelConfig {
    private RenderState renderState = RenderState.DEFAULT;
    private boolean writeGBuffer = true;
    private boolean emissive = false;
    private float emissiveStrength = 1.0f;
    private boolean castsShadow = true;

    private boolean frustumCull = true;

    private boolean lod = false;

    private ResourceLocation customVsh;
    private ResourceLocation customFsh;

    public RenderState renderState() {
        return renderState;
    }

    public boolean writeGBuffer() {
        return writeGBuffer;
    }

    public boolean castsShadow() {
        return castsShadow;
    }

    public ModelConfig castsShadow(boolean value) {
        this.castsShadow = value;
        return this;
    }

    public boolean frustumCull() {
        return frustumCull;
    }

    public ModelConfig frustumCull(boolean value) {
        this.frustumCull = value;
        return this;
    }

    public ModelConfig lod(boolean value) {
        this.lod = value;
        return this;
    }

    public boolean isLod() {
        return lod;
    }

    public boolean isEmissive() {
        return emissive;
    }

    public float emissiveStrength() {
        return emissiveStrength;
    }

    public ModelConfig renderState(RenderState state) {
        this.renderState = state;
        return this;
    }

    public ModelConfig writeGBuffer(boolean value) {
        this.writeGBuffer = value;
        return this;
    }

    public ModelConfig emissive() {
        return emissive(1.0f);
    }

    public ModelConfig emissive(float strength) {
        this.emissive = true;
        this.emissiveStrength = strength;
        return this;
    }

    public ModelConfig shaders(ResourceLocation vsh, ResourceLocation fsh) {
        this.customVsh = vsh;
        this.customFsh = fsh;
        return this;
    }

    public ModelConfig shader(ResourceLocation id) {
        return shaders(id, id);
    }

    public boolean hasCustomShader() {
        return customVsh != null && customFsh != null;
    }

    public ResourceLocation customVsh() {
        return customVsh;
    }

    public ResourceLocation customFsh() {
        return customFsh;
    }

    public ModelConfig copy() {
        ModelConfig c = new ModelConfig();
        c.renderState = renderState;
        c.writeGBuffer = writeGBuffer;
        c.emissive = emissive;
        c.emissiveStrength = emissiveStrength;
        c.castsShadow = castsShadow;
        c.frustumCull = frustumCull;
        c.lod = lod;
        c.customVsh = customVsh;
        c.customFsh = customFsh;
        return c;
    }
}
