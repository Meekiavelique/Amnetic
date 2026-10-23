package com.meekdev.amnetic.client.entityfx.internal;

import com.meekdev.amnetic.client.particle.SceneDepth;
import com.mojang.blaze3d.pipeline.BlendFunction;
//? if >=26.1 {
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
//?} else {
/*import com.mojang.blaze3d.platform.DepthTestFunction;
*///?}
import com.mojang.blaze3d.pipeline.RenderPipeline;
//? if >=26.2 {
/*import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import net.minecraft.client.renderer.BindGroupLayouts;
*///?}
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.AmneticRenderTypeAccess;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class EntityEffectPipeline {

    private EntityEffectPipeline() {}

    public static RenderType build(String debugName, Identifier vsh, Identifier fsh,
                                   Identifier uniformTexId, Identifier skinId,
                                   boolean needsSceneColor, boolean needsDepth,
                                   Map<String, Identifier> extraSamplers) {
        //? if >=26.2 {
        /*BindGroupLayout.Builder samplers = BindGroupLayout.builder().withSampler("UniformSampler");
        if (needsSceneColor) samplers.withSampler("SceneColorSampler");
        if (skinId != null) samplers.withSampler("Sampler0");
        if (needsDepth) samplers.withSampler("DepthSampler");
        for (String name : extraSamplers.keySet()) samplers.withSampler(name);
        RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(fsh.getNamespace(), "entityfx_pipeline/" + fsh.getPath()))
                .withVertexShader(vsh)
                .withFragmentShader(fsh)
                .withBindGroupLayout(samplers.build())
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                // 26.2 renders with reversed depth
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
        *///?} else {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(fsh.getNamespace(), "entityfx_pipeline/" + fsh.getPath()))
                .withVertexShader(vsh)
                .withFragmentShader(fsh)
                .withSampler("UniformSampler");
        if (needsSceneColor) builder.withSampler("SceneColorSampler");
        if (skinId != null) builder.withSampler("Sampler0");
        if (needsDepth) builder.withSampler("DepthSampler");
        for (String name : extraSamplers.keySet()) builder.withSampler(name);
        RenderPipeline pipeline = builder
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
        //?}
                //? if >=26.1 <26.2 {
                .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
                //?} else if <26.1 {
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

        return AmneticRenderTypeAccess.create(debugName, setup.createRenderSetup());
    }
}
