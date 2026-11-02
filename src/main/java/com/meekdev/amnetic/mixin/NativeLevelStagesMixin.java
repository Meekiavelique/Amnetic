package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.AmneticClient;
import com.meekdev.amnetic.client.render.LevelCamera;
import com.meekdev.amnetic.client.gbuffer.GBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(LevelRenderer.class)
public abstract class NativeLevelStagesMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private net.minecraft.client.renderer.state.level.LevelRenderState levelRenderState;
    @Inject(method="render",at=@At("HEAD"))
    private void amnetic$beginMaterials(CallbackInfo ci) {
        if(!com.meekdev.amnetic.client.scene.internal.CaptureManager.INSTANCE.isCapturing())GBuffer.beginFrame();
        com.meekdev.amnetic.client.gbuffer.internal.NativeWorldMaterials.world=GBuffer.isEnabled() && !com.meekdev.amnetic.client.compat.IrisCompat.shadersActive() && !com.meekdev.amnetic.client.scene.internal.CaptureManager.INSTANCE.isCapturing();
    }
    @Inject(method="render",at=@At("TAIL"))
    private void amnetic$finishWorld(CallbackInfo ci) {
        com.meekdev.amnetic.client.gbuffer.internal.NativeWorldMaterials.world=false;
        var camera=LevelCamera.of(levelRenderState.cameraRenderState);
        com.meekdev.amnetic.client.render.NativeCommandBatch.begin();
        try {AmneticClient.beforeTranslucent(camera);AmneticClient.endMain(camera);AmneticClient.renderPost();}
        finally {com.meekdev.amnetic.client.render.NativeCommandBatch.end();}
    }
}
