package com.meekdev.amnetic.client.post.internal;

import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class PipelineBuilder {

    private PipelineBuilder() {}

    public static PostEffectPipeline build(
            PostEffectPipeline base,
            Map<String, Supplier<List<UniformValue>>> uniformSlots,
            Map<String, Identifier> textureOverrides
    ) {
        if (uniformSlots.isEmpty() && textureOverrides.isEmpty()) {
            return base;
        }

        List<PostEffectPipeline.Pass> newPasses = new ArrayList<>(base.passes().size());
        for (PostEffectPipeline.Pass pass : base.passes()) {
            newPasses.add(buildPass(pass, uniformSlots, textureOverrides));
        }

        return new PostEffectPipeline(base.internalTargets(), newPasses);
    }

    private static PostEffectPipeline.Pass buildPass(
            PostEffectPipeline.Pass pass,
            Map<String, Supplier<List<UniformValue>>> uniformSlots,
            Map<String, Identifier> textureOverrides
    ) {
        Map<String, List<UniformValue>> newUniforms = new HashMap<>(pass.uniforms());
        for (Map.Entry<String, Supplier<List<UniformValue>>> slot : uniformSlots.entrySet()) {
            newUniforms.put(slot.getKey(), slot.getValue().get());
        }

        List<PostEffectPipeline.Input> newInputs;
        if (textureOverrides.isEmpty()) {
            newInputs = pass.inputs();
        } else {
            newInputs = new ArrayList<>(pass.inputs().size());
            for (PostEffectPipeline.Input input : pass.inputs()) {
                if (input instanceof PostEffectPipeline.TextureSampler ts) {
                    Identifier override = textureOverrides.get(ts.samplerName());
                    if (override != null) {
                        newInputs.add(new PostEffectPipeline.TextureSampler(
                                ts.samplerName(), override, ts.width(), ts.height(), ts.bilinear()
                        ));
                        continue;
                    }
                }
                newInputs.add(input);
            }
        }

        return new PostEffectPipeline.Pass(
                pass.vertexShaderId(),
                pass.fragmentShaderId(),
                newInputs,
                pass.outputTarget(),
                newUniforms
        );
    }
}
