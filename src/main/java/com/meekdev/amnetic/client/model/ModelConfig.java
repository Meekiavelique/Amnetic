package com.meekdev.amnetic.client.model;

import com.meekdev.amnetic.client.instanced.RenderState;
import net.minecraft.resources.Identifier;

public final class ModelConfig {

    private RenderState renderState = RenderState.DEFAULT;
    private boolean writeGBuffer = true;
    private boolean emissive = false;
    private float emissiveStrength = 1.0f;
    private boolean castsShadow = true;

    // per-model frustum culling, on by default. turn off for tiled geometry like streamed terrain chunks
    // where the per-chunk AABB test can wrongly drop on-screen chunks and leave holes
    private boolean frustumCull = true;

    // automatic distance LOD (QEM simplification, pregenerated on a background thread). opt-in per model,
    // enable on heavy props and leave flat terrain at full detail
    private boolean lod = false;

    // optional custom GLSL program, raw ids resolved to shaders/<path>.vsh|.fsh at load like the
    // instancing shaders. null means the built-in model shader
    private Identifier customVsh;
    private Identifier customFsh;

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

    // false draws unconditionally, wanted for streamed terrain chunks
    public ModelConfig frustumCull(boolean value) {
        this.frustumCull = value;
        return this;
    }

    // opt-in automatic distance LOD, simplification pregenerated off-thread
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

    /**
     * custom GLSL program instead of the built-in one. the shader gets the standard attribute layout
     * (0 Position, 1 Normal, 2 UV, 3-6 instance matrix, 7 InstLight, 8 Tangent, 9 Joints, 10 Weights)
     * and whatever uniforms it declares from the engine's set (ProjViewMatrix, Time, lighting, material,
     * samplers, EmissiveStrength, skinning). fragment stage writes the gbuffer. ids are raw, e.g.
     * {@code "stun:model_snap"} resolves to {@code stun:shaders/model_snap.vsh|.fsh}
     */
    public ModelConfig shaders(Identifier vsh, Identifier fsh) {
        this.customVsh = vsh;
        this.customFsh = fsh;
        return this;
    }

    // shorthand for shaders() when the .vsh and .fsh share the same base id
    public ModelConfig shader(Identifier id) {
        return shaders(id, id);
    }

    public boolean hasCustomShader() {
        return customVsh != null && customFsh != null;
    }

    public Identifier customVsh() {
        return customVsh;
    }

    public Identifier customFsh() {
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
