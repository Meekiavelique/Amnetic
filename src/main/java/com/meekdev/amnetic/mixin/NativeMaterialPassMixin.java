package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.gbuffer.internal.*;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import java.util.ArrayList;
import java.util.Optional;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(FrontendCommandEncoder.class)
public abstract class NativeMaterialPassMixin {
    @ModifyVariable(method="createRenderPass(Lcom/mojang/renderpearl/api/commands/RenderPassDescriptor;)Lcom/mojang/renderpearl/api/commands/RenderPass;",at=@At("HEAD"),argsOnly=true)
    private RenderPassDescriptor amnetic$attachMaterials(RenderPassDescriptor descriptor) {
        if(!NativeWorldMaterials.world || descriptor.colorAttachments().size()!=1 || descriptor.depthAttachment()==null)return descriptor;
        var main=Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if(descriptor.colorAttachments().getFirst().textureView().texture()!=main.getColorTexture())return descriptor;
        var attachments=new ArrayList<>(descriptor.colorAttachments());
        for(int i=0;i<4;i++)attachments.add(new RenderPassDescriptor.Attachment<>(GBufferTargets.INSTANCE.nativeColorView(i),Optional.empty()));
        return new RenderPassDescriptor(descriptor.label(),attachments,descriptor.depthAttachment(),descriptor.renderArea());
    }
}
