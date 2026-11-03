package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.gbuffer.internal.NativeWorldMaterials;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import org.joml.Vector4fc;
import java.util.List;
import java.util.Optional;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(FrontendRenderPass.class)
public abstract class NativeMaterialBindMixin {
    @Shadow @Final private List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> colorAttachments;
    @ModifyVariable(method="setPipeline",at=@At("HEAD"),argsOnly=true)
    private CompiledRenderPipeline amnetic$materialPipeline(CompiledRenderPipeline pipeline) {
        return NativeWorldMaterials.world && colorAttachments.size()==5?NativeWorldMaterials.variant(pipeline):pipeline;
    }
}
