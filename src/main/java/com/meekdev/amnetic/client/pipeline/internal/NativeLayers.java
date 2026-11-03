package com.meekdev.amnetic.client.pipeline.internal;

import com.meekdev.amnetic.client.framebuffer.*;
import com.meekdev.amnetic.client.pipeline.*;
import com.meekdev.amnetic.client.render.*;
import com.meekdev.amnetic.client.compat.IrisCompat;
import com.meekdev.amnetic.client.gbuffer.GBuffer;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferNormalFill;
import com.meekdev.amnetic.client.gbuffer.internal.NativeWorldMaterials;
import com.meekdev.amnetic.client.light.internal.DeferredLightingPass;
import com.meekdev.amnetic.client.light.internal.LightRegistry;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import com.meekdev.amnetic.client.scene.internal.CaptureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import java.util.EnumMap;
import java.util.Optional;

final class NativeLayers implements AutoCloseable {
    private final EnumMap<RenderLayer,CaptureTarget> targets=new EnumMap<>(RenderLayer.class);
    private final EnumMap<RenderLayer,Boolean> active=new EnumMap<>(RenderLayer.class);
    private Framebuffer workA,workB;
    private NativeScreenProgram composite;
    boolean begin(RenderLayer layer) {
        if(CaptureManager.INSTANCE.isCapturing() || layer!=RenderLayer.HAND && Pipeline.layerPasses(layer).stream().noneMatch(p->p.enabled()&&p.supportsNative()))return false;
        var target=targets.computeIfAbsent(layer,key->new CaptureTarget()).ensure(1);
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(target.getColorTexture(),new Vector4f(0));
        encoder.clearDepthTexture(target.getDepthTexture(),0);
        encoder.submit();LayerRedirect.set(target);active.put(layer,true);
        if(layer==RenderLayer.HAND) {
            GBuffer.beginFrame();
            NativeWorldMaterials.world=GBuffer.isEnabled() && !IrisCompat.shadersActive();
        }
        return true;
    }
    void end(RenderLayer kind) {
        NativeCommandBatch.begin();
        try {endBatched(kind);}finally{NativeCommandBatch.end();}
    }
    private void endBatched(RenderLayer kind) {
        if(!Boolean.TRUE.equals(active.remove(kind)))return;
        if(kind==RenderLayer.HAND) {
            NativeWorldMaterials.world=false;
            try {
                var camera=CameraSnapshot.current();boolean local=false;
                if(camera!=null)for(var light:LightRegistry.INSTANCE.all()) {
                    if(light.isEnabled()&&light.type().id()!=2&&light.intensity()>0
                            && camera.eye.distanceToSqr(light.x(),light.y(),light.z())<Math.pow(light.range()+2.5,2)){local=true;break;}
                }
                if(local) {
                    GBufferNormalFill.INSTANCE.render();
                    DeferredLightingPass.INSTANCE.renderSurface();
                }
            } finally {CameraSnapshot.endHand();}
        }
        LayerRedirect.set(null);
        var target=targets.get(kind).target();var main=Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if(workA==null) {
            var spec=FramebufferSpec.builder().color(ColorFormat.RGBA8).build();
            workA=Framebuffers.screen("Native layer work A",spec);workB=Framebuffers.screen("Native layer work B",spec);
        }
        var input=target.getColorTextureView();boolean a=true;
        var context=new FrameContext(CameraSnapshot.current(),null,0);
        for(var pass:Pipeline.layerPasses(kind)) {
            if(!pass.enabled()||!pass.supportsNative())continue;
            var output=a?workA:workB;output.colorTextureView(0);
            pass.renderNative(context,new Layer(kind,input,output.renderPassDescriptor(),target.width,target.height));
            input=output.colorTextureView(0);a=!a;
        }
        if(composite==null)composite=new NativeScreenProgram(Identifier.fromNamespaceAndPath("amnetic","shaders/layer/composite.fsh"),
                new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT),main.getColorTexture().getFormat(),15));
        composite.setTexture("LayerSampler",input);
        composite.draw(RenderPassDescriptor.builder(()->"Amnetic native layer composite").withColorAttachment(main.getColorTextureView()).build());
    }
    @Override public void close() {
        if(workA!=null)workA.dispose();if(workB!=null)workB.dispose();if(composite!=null)composite.close();
        for(var target:targets.values())if(target.target()!=null)target.target().destroyBuffers();
        targets.clear();active.clear();workA=workB=null;composite=null;
    }
}
