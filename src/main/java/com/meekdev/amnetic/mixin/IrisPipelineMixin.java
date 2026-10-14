package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.AmneticClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"net.irisshaders.iris.pipeline.IrisRenderingPipeline",
        "net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline"}, remap = false)
public abstract class IrisPipelineMixin {
    @Inject(method = "finalizeLevelRendering", at = @At("RETURN"), require = 0, remap = false)
    private void amnetic$lightShaderScene(CallbackInfo ci) {
        AmneticClient.renderShaderLights();
    }
}
