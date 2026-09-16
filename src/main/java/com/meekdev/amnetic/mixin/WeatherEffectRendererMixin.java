package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import net.minecraft.client.renderer.WeatherEffectRenderer;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.WeatherRenderState;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.WeatherRenderState;
*///?}
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    //? if >=26.1 {
    private void amnetic$skipWeatherInCapture(Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
    //?} else {
    /*private void amnetic$skipWeatherInCapture(MultiBufferSource buffers, Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
    *///?}
        if (CaptureManager.INSTANCE.isCapturing()) ci.cancel();
    }
}
