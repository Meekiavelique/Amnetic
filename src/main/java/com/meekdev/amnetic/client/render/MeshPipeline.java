package com.meekdev.amnetic.client.render;

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
import java.util.HashMap;
import java.util.Map;
import com.meekdev.amnetic.mixin.accessor.RenderTypeInvoker;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;

public final class MeshPipeline {

    private static final Map<Identifier, RenderPipeline> PIPELINES = new HashMap<>();
    private static final Map<String, RenderType> RENDER_TYPES = new HashMap<>();

    //? if >=26.2 {
    /*private static final BindGroupLayout SAMPLERS = BindGroupLayout.builder()
            .withSampler("Sampler0").withSampler("Sampler1").withSampler("DepthSampler").build();
    *///?}

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
                /*.withBindGroupLayout(SAMPLERS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                *///?} else {
                .withSampler("Sampler0")
                .withSampler("Sampler1")
                .withSampler("DepthSampler")
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.QUADS)
                //?}
                //? if >=26.2 {
                /*.withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
                *///?} else if >=26.1 {
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
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
                /*.withBindGroupLayout(SAMPLERS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                *///?} else {
                .withSampler("Sampler0")
                .withSampler("Sampler1")
                .withSampler("DepthSampler")
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.QUADS)
                //?}
                //? if >=26.2 {
                /*.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                *///?} else if >=26.1 {
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                //?} else {
                /*.withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                *///?}
                .withCull(true)
                .build();
    }
}
