package com.meekdev.amnetic.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.meekdev.amnetic.client.gbuffer.internal.NativeWorldMaterials;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import java.util.concurrent.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FrontendGpuDevice.class)
public abstract class NativeMaterialPipelinesMixin {
    @WrapMethod(method="compilePipeline")
    private CompletableFuture<CompiledRenderPipeline.Pending> amnetic$materials(RenderPipeline pipeline,ShaderSource source,Executor executor,
            Operation<CompletableFuture<CompiledRenderPipeline.Pending>> original) {
        var normal=original.call(pipeline,source,executor);
        if(!NativeWorldMaterials.eligible(pipeline))return normal;
        var variant=original.call(NativeWorldMaterials.definition(pipeline),NativeWorldMaterials.source(source,NativeWorldMaterials.captures(pipeline)),executor);
        return normal.thenCombine(variant,(a,b)->()->{
            var compiled=a.finishCompile();var material=b.finishCompile();
            NativeWorldMaterials.put(compiled,material,pipeline.getLocation().getNamespace().equals("sodium"));return compiled;
        });
    }
}
