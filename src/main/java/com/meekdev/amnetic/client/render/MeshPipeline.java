package com.meekdev.amnetic.client.render;

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
import java.util.HashMap;
import java.util.Map;
import com.meekdev.amnetic.mixin.accessor.RenderTypeInvoker;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;

public final class MeshPipeline {

    private static final Map<Identifier, RenderPipeline> PIPELINES = new HashMap<>();
    private static final Map<String, RenderType> RENDER_TYPES = new HashMap<>();

    private MeshPipeline() {}

    public static RenderType renderType(Identifier vertexShader, Identifier fragmentShader,
                                        Identifier texture0, Identifier texture1) {
        String key = vertexShader + "|" + fragmentShader + "|" + texture0 + "|" + texture1;
        return RENDER_TYPES.computeIfAbsent(key, k -> {
            RenderPipeline pipeline = PIPELINES.computeIfAbsent(fragmentShader,
                    fsh -> buildPipeline(vertexShader, fsh));
            RenderSetup setup = RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", texture0)
                    .withTexture("Sampler1", texture1)
                    .withTexture("DepthSampler", SceneDepth.ID)
                    .createRenderSetup();
            return RenderTypeInvoker.amnetic$create("amnetic_mesh/" + RENDER_TYPES.size(), setup);
        });
    }

    public static RenderType cutoutRenderType(Identifier vertexShader, Identifier fragmentShader,
                                              Identifier texture0, Identifier texture1) {
        String key = "cutout|" + vertexShader + "|" + fragmentShader + "|" + texture0 + "|" + texture1;
        return RENDER_TYPES.computeIfAbsent(key, k -> {
            Identifier location = Identifier.fromNamespaceAndPath(
                    fragmentShader.getNamespace(), "cutout/" + fragmentShader.getPath());
            RenderPipeline pipeline = PIPELINES.computeIfAbsent(location,
                    loc -> buildCutoutPipeline(loc, vertexShader, fragmentShader));
            RenderSetup setup = RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", texture0)
                    .withTexture("Sampler1", texture1)
                    .withTexture("DepthSampler", SceneDepth.ID)
                    .createRenderSetup();
            return RenderTypeInvoker.amnetic$create("amnetic_mesh_cutout/" + RENDER_TYPES.size(), setup);
        });
    }

    private static RenderPipeline buildCutoutPipeline(Identifier location, Identifier vertexShader,
                                                      Identifier fragmentShader) {
        return RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader(vertexShader)
                .withFragmentShader(fragmentShader)
                //? if >=26.2 {
                .withBindGroupLayout(BindGroupLayout.builder()
                        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                        .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                        .withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
                        .withUniform("Sampler1", UniformType.COMBINED_IMAGE_SAMPLER)
                        .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER).build())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                //?} else {
                /*.withSampler("Sampler0")
                .withSampler("Sampler1")
                .withSampler("DepthSampler")
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.QUADS)
                *///?}
                //? if >=26.1 {
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(new DepthStencilState(com.meekdev.amnetic.client.compat.VanillaCompat.reversedDepth() ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.LESS_THAN_OR_EQUAL, true))
                //?} else {
                /*.withoutBlend()
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(true)
                *///?}
                .withCull(false)
                .build();
    }

    private static RenderPipeline buildPipeline(Identifier vertexShader, Identifier fragmentShader) {
        return RenderPipeline.builder()
                .withLocation(fragmentShader)
                .withVertexShader(vertexShader)
                .withFragmentShader(fragmentShader)
                //? if >=26.2 {
                .withBindGroupLayout(BindGroupLayout.builder()
                        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                        .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                        .withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
                        .withUniform("Sampler1", UniformType.COMBINED_IMAGE_SAMPLER)
                        .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER).build())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                //?} else {
                /*.withSampler("Sampler0")
                .withSampler("Sampler1")
                .withSampler("DepthSampler")
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.QUADS)
                *///?}
                //? if >=26.1 {
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(com.meekdev.amnetic.client.compat.VanillaCompat.reversedDepth() ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.LESS_THAN_OR_EQUAL, false))
                //?} else {
                /*.withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                *///?}
                .withCull(true)
                .build();
    }
}
