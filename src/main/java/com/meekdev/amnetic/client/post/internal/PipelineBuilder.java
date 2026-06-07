package com.meekdev.amnetic.client.post.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;

public final class PipelineBuilder {

    private PipelineBuilder() {}

    public static PostChainConfig build(
            PostChainConfig base,
            Map<String, Supplier<List<UniformValue>>> uniformSlots,
            Map<String, Identifier> textureOverrides
    ) {
        if (uniformSlots.isEmpty() && textureOverrides.isEmpty()) {
            return base;
        }

        List<PostChainConfig.Pass> newPasses = new ArrayList<>(base.passes().size());
        for (PostChainConfig.Pass pass : base.passes()) {
            newPasses.add(buildPass(pass, uniformSlots, textureOverrides));
        }

        return new PostChainConfig(base.internalTargets(), newPasses);
    }

    private static PostChainConfig.Pass buildPass(
            PostChainConfig.Pass pass,
            Map<String, Supplier<List<UniformValue>>> uniformSlots,
            Map<String, Identifier> textureOverrides
    ) {
        Map<String, List<UniformValue>> newUniforms = new HashMap<>(pass.uniforms());
        for (Map.Entry<String, Supplier<List<UniformValue>>> slot : uniformSlots.entrySet()) {
            newUniforms.put(slot.getKey(), slot.getValue().get());
        }

        List<PostChainConfig.Input> newInputs;
        if (textureOverrides.isEmpty()) {
            newInputs = pass.inputs();
        } else {
            newInputs = new ArrayList<>(pass.inputs().size());
            for (PostChainConfig.Input input : pass.inputs()) {
                if (input instanceof PostChainConfig.TextureInput ts) {
                    Identifier override = textureOverrides.get(ts.samplerName());
                    if (override != null) {
                        newInputs.add(new PostChainConfig.TextureInput(
                                ts.samplerName(), override, ts.width(), ts.height(), ts.bilinear()
                        ));
                        continue;
                    }
                }
                newInputs.add(input);
            }
        }

        return new PostChainConfig.Pass(
                pass.vertexShaderId(),
                pass.fragmentShaderId(),
                newInputs,
                pass.outputTarget(),
                newUniforms
        );
    }
}
