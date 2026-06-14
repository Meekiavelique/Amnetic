package com.meekdev.amnetic.client.entityfx.internal;

import com.meekdev.amnetic.client.particle.SceneDepth;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
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
                .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
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
