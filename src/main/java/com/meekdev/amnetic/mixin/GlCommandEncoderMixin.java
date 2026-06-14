package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public class GlCommandEncoderMixin {

    @Redirect(
            method = "applyPipelineState",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderPipeline;isCull()Z")
    )
    private boolean amnetic$noCullDuringCapture(RenderPipeline pipeline) {
        return pipeline.isCull() && !CaptureManager.INSTANCE.isCapturing();
    }
}
