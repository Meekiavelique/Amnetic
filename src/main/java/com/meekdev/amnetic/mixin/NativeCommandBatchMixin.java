package com.meekdev.amnetic.mixin;
import com.meekdev.amnetic.client.render.NativeCommandBatch;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrontendCommandEncoder.class)
public abstract class NativeCommandBatchMixin {
    @Inject(method="submit",at=@At("HEAD"),cancellable=true)
    private void amnetic$batch(CallbackInfo ci){if(NativeCommandBatch.defer())ci.cancel();}
}
