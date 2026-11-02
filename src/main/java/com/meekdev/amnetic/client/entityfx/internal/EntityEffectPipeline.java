package com.meekdev.amnetic.client.entityfx.internal;

import com.meekdev.amnetic.client.particle.SceneDepth;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
//? if >=26.1 {
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
//?} else {
/*import com.mojang.blaze3d.platform.DepthTestFunction;
*///?}
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
//? if >=26.2 {
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
//?}
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.Map;
import com.meekdev.amnetic.mixin.accessor.RenderTypeInvoker;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class EntityEffectPipeline {

    private EntityEffectPipeline() {}

    public static RenderType build(String debugName, Identifier vsh, Identifier fsh,
                                   Identifier uniformTexId, Identifier skinId,
                                   boolean needsSceneColor, boolean needsDepth,
                                   Map<String, Identifier> extraSamplers) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(fsh.getNamespace(), "entityfx_pipeline/" + fsh.getPath()))
                .withVertexShader(vsh)
                .withFragmentShader(fsh)
                //? if >=26.2 {
                ;
        var bindings = BindGroupLayout.builder()
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withUniform("UniformSampler", UniformType.COMBINED_IMAGE_SAMPLER);
        if (needsSceneColor) bindings.withUniform("SceneColorSampler", UniformType.COMBINED_IMAGE_SAMPLER);
        if (skinId != null) bindings.withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER);
        if (needsDepth) bindings.withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER);
        for (String name : extraSamplers.keySet()) bindings.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
        builder.withBindGroupLayout(bindings.build());
                //?} else {
                /*.withSampler("UniformSampler");
        if (needsSceneColor) builder.withSampler("SceneColorSampler");
        if (skinId != null) builder.withSampler("Sampler0");
        if (needsDepth) builder.withSampler("DepthSampler");
        for (String name : extraSamplers.keySet()) builder.withSampler(name);
        *///?}
        RenderPipeline pipeline = builder
                //? if >=26.2 {
                .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                //?} else {
                /*.withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                //? if >=26.1 {
                .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
                //?}
                *///?}
                //? if >=26.1 {
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(com.meekdev.amnetic.client.compat.VanillaCompat.reversedDepth() ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.LESS_THAN_OR_EQUAL, true))
                //?} else {
                /*.withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(true)
                *///?}
                .withCull(true)
                .build();

        RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipeline)
                .withTexture("UniformSampler", uniformTexId);
        if (needsSceneColor) setup.withTexture("SceneColorSampler", SceneColorSnapshot.ID);
        if (skinId != null) setup.withTexture("Sampler0", skinId);
        if (needsDepth) setup.withTexture("DepthSampler", SceneDepth.ID);
        for (Map.Entry<String, Identifier> e : extraSamplers.entrySet()) {
            setup.withTexture(e.getKey(), e.getValue());
        }

        return RenderTypeInvoker.amnetic$create(debugName, setup.createRenderSetup());
    }
}
