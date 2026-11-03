package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.AmneticClient;
import com.meekdev.amnetic.client.compat.IrisCompat;
import com.meekdev.amnetic.client.gbuffer.GBuffer;
import com.meekdev.amnetic.client.gbuffer.internal.NativeWorldMaterials;
import com.meekdev.amnetic.client.render.LevelCamera;
import com.meekdev.amnetic.client.render.NativeCommandBatch;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(LevelRenderer.class)
public abstract class NativeLevelStagesMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private LevelRenderState levelRenderState;
    @Inject(method="render",at=@At("HEAD"))
    private void amnetic$beginMaterials(CallbackInfo ci) {
        if(!CaptureManager.INSTANCE.isCapturing())GBuffer.beginFrame();
        NativeWorldMaterials.world=GBuffer.isEnabled() && !IrisCompat.shadersActive() && !CaptureManager.INSTANCE.isCapturing();
    }
    @Inject(method="render",at=@At("TAIL"))
    private void amnetic$finishWorld(CallbackInfo ci) {
        NativeWorldMaterials.world=false;
        var camera=LevelCamera.of(levelRenderState.cameraRenderState);
        NativeCommandBatch.begin();
        try {AmneticClient.beforeTranslucent(camera);AmneticClient.endMain(camera);AmneticClient.renderPost();}
        finally {NativeCommandBatch.end();}
    }
}
