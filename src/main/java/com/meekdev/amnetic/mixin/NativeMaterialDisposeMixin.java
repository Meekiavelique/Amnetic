package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.gbuffer.internal.NativeWorldMaterials;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrontendRenderPipeline.class)
public abstract class NativeMaterialDisposeMixin {
    @Inject(method="close",at=@At("HEAD"))
    private void amnetic$closeMaterials(CallbackInfo ci){NativeWorldMaterials.release((CompiledRenderPipeline)(Object)this);}
}
