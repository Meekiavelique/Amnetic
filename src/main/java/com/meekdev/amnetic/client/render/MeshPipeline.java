package com.meekdev.amnetic.client.render;

import com.meekdev.amnetic.client.particle.SceneDepth;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.AmneticRenderTypeAccess;
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
            return AmneticRenderTypeAccess.create("amnetic_mesh/" + RENDER_TYPES.size(), setup);
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
            return AmneticRenderTypeAccess.create("amnetic_mesh_cutout/" + RENDER_TYPES.size(), setup);
        });
    }

    private static RenderPipeline buildCutoutPipeline(Identifier location, Identifier vertexShader,
                                                      Identifier fragmentShader) {
        return RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader(vertexShader)
                .withFragmentShader(fragmentShader)
                .withSampler("Sampler0")
                .withSampler("Sampler1")
                .withSampler("DepthSampler")
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.QUADS)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
                .withCull(false)
                .build();
    }

    private static RenderPipeline buildPipeline(Identifier vertexShader, Identifier fragmentShader) {
        return RenderPipeline.builder()
                .withLocation(fragmentShader)
                .withVertexShader(vertexShader)
                .withFragmentShader(fragmentShader)
                .withSampler("Sampler0")
                .withSampler("Sampler1")
                .withSampler("DepthSampler")
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .withCull(true)
                .build();
    }
}
