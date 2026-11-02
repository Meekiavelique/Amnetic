package com.meekdev.amnetic.client.decal.internal;

import com.meekdev.amnetic.client.decal.Decal;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.ImportedTextures;
import com.meekdev.amnetic.client.render.NativeScreenProgram;
import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


final class NativeDecalRenderer implements AutoCloseable {
    private static final class Visible {
        final Decal decal;final Matrix4f inverse=new Matrix4f();
        final Vector3f normal=new Vector3f(),right=new Vector3f(),up=new Vector3f();
        int x,y,width,height;float block,sky;long lightTick=Long.MIN_VALUE;
        Identifier textureId,normalId;GpuTextureView texture,normalTexture;
        Visible(Decal decal){this.decal=decal;}
    }
    private final java.util.IdentityHashMap<Decal,Visible> cache=new java.util.IdentityHashMap<>();
    private final Set<Decal> active=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private net.minecraft.client.multiplayer.ClientLevel level;
    private final BlockPos.MutableBlockPos lightPos=new BlockPos.MutableBlockPos();
    private final DecalBounds projectedBounds = new DecalBounds();
    private final org.joml.FrustumIntersection frustum = new org.joml.FrustumIntersection();
    private final ArrayList<Visible> visible=new ArrayList<>();
    private final Set<Identifier> loaded=new HashSet<>();
    private NativeScreenProgram color,material;
    private com.mojang.renderpearl.api.textures.GpuTexture white;
    private GpuTextureView whiteView;
    private final Matrix4f model=new Matrix4f();
    private final int[] viewport=new int[4],bounds=new int[4];
    private static ColorTargetState target(GpuFormat format, boolean write) {
        return new ColorTargetState(write?Optional.of(BlendFunction.TRANSLUCENT):Optional.empty(),format,write?15:0);
    }
    void render(List<Decal> decals) {
        var mc=Minecraft.getInstance();var camera=CameraSnapshot.current();
        if(camera==null || mc.level==null)return;
        if(level!=mc.level){cache.clear();level=mc.level;}
        if(cache.size()>decals.size()+64) {
            active.clear();active.addAll(decals);
            cache.keySet().removeIf(d->d.isRemoved()||!active.contains(d));
            active.clear();
        }
        var main=mc.gameRenderer.mainRenderTarget();
        viewport[2]=main.width;viewport[3]=main.height;
        visible.clear();
        frustum.set(camera.viewProj);
        {
            for(var decal:decals) {
                if(decal.isRemoved() || decal.opacity()<=0 || decal.width()<=0 || decal.height()<=0 || decal.depth()<=0)continue;
                var center=decal.center();double radius=.5*Math.sqrt(decal.width()*decal.width()+decal.height()*decal.height()+decal.depth()*decal.depth())+.002;
                if(!frustum.testSphere((float)(center.x-camera.eye.x),(float)(center.y-camera.eye.y),(float)(center.z-camera.eye.z),(float)radius)) continue;
                var item=cache.computeIfAbsent(decal,Visible::new);
                var normal=item.normal.set(decal.normal()).normalize();
                var right=item.right.set(normal).cross(0,1,0);if(right.lengthSquared()<1e-6f)right.set(1,0,0);right.normalize();
                var up=item.up.set(right).cross(normal).normalize();
                model.identity().m00(right.x*decal.width()).m01(right.y*decal.width()).m02(right.z*decal.width())
                        .m10(normal.x*decal.depth()).m11(normal.y*decal.depth()).m12(normal.z*decal.depth())
                        .m20(up.x*decal.height()).m21(up.y*decal.height()).m22(up.z*decal.height())
                        .m30((float)(center.x-camera.eye.x)).m31((float)(center.y-camera.eye.y)).m32((float)(center.z-camera.eye.z));
                if (!projectedBounds.compute(camera.viewProj, model, true, main.width, main.height)) continue;
                System.arraycopy(projectedBounds.rect(), 0, bounds, 0, 4);
                if(item.lightTick!=mc.level.getGameTime()) {
                    lightPos.set(center.x+normal.x*.025,center.y+normal.y*.025,center.z+normal.z*.025);
                    item.block=(mc.level.getBrightness(LightLayer.BLOCK,lightPos)+.5f)/16;
                    item.sky=(mc.level.getBrightness(LightLayer.SKY,lightPos)+.5f)/16;item.lightTick=mc.level.getGameTime();
                }
                item.inverse.set(model).invert();item.x=bounds[0];item.y=bounds[1];item.width=bounds[2];item.height=bounds[3];visible.add(item);
            }
        }
        if(visible.isEmpty())return; // No texture bindings, shader compilation or GPU passes for invisible decals.
        if(color==null) {
            java.util.function.UnaryOperator<String> reverse=source->source.replace("depth >= 1.0","depth <= 0.0");
            color=new NativeScreenProgram(Identifier.fromNamespaceAndPath("amnetic","shaders/decal/decal.fsh"),reverse,target(main.getColorTexture().getFormat(),true));
            material=new NativeScreenProgram(Identifier.fromNamespaceAndPath("amnetic","shaders/decal/decal_gbuffer.fsh"),reverse,
                    target(main.getColorTexture().getFormat(),false),target(GpuFormat.RGBA16_FLOAT,true),target(GpuFormat.RGBA8_UNORM,true),
                    target(GpuFormat.RGBA16_FLOAT,false),target(GpuFormat.RGBA8_UNORM,true));
        }
        if(white==null) {
            var device=RenderSystem.getDevice();
            white=device.createTexture("Amnetic decal fallback",com.mojang.renderpearl.api.textures.GpuTexture.USAGE_TEXTURE_BINDING
                    |com.mojang.renderpearl.api.textures.GpuTexture.USAGE_COPY_DST
                    |com.mojang.renderpearl.api.textures.GpuTexture.USAGE_RENDER_ATTACHMENT,GpuFormat.RGBA8_UNORM,1,1,1,1);
            whiteView=device.createTextureView(white);
            var clear=device.createCommandEncoder();clear.clearColorTexture(white,new org.joml.Vector4f(1));clear.submit();
        }
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        var colorPass=RenderPassDescriptor.builder(()->"Amnetic decal color").withColorAttachment(main.getColorTextureView()).build();
        var gbuffer=GBufferTargets.INSTANCE;
        RenderPassDescriptor materials=gbuffer.isPopulated()?RenderPassDescriptor.builder(()->"Amnetic decal material")
                .withColorAttachment(main.getColorTextureView()).withColorAttachment(gbuffer.nativeColorView(0))
                .withColorAttachment(gbuffer.nativeColorView(1)).withColorAttachment(gbuffer.nativeColorView(2))
                .withColorAttachment(gbuffer.nativeColorView(3)).build():null;
        for(var item:visible) {
            if(item.texture==null||item.texture.isClosed()||!java.util.Objects.equals(item.textureId,item.decal.texture())) {
                item.textureId=item.decal.texture();item.texture=item.textureId==null?whiteView:texture(item.textureId);
            }
            if(item.normalTexture==null||item.normalTexture.isClosed()||!java.util.Objects.equals(item.normalId,item.decal.normalMap())) {
                item.normalId=item.decal.normalMap();item.normalTexture=item.normalId==null?whiteView:texture(item.normalId);
            }
        }
        color.prepare();
        configureCommon(color,camera,main.getDepthTextureView());
        color.setInt("TppLitWater",0);color.setInt("TppWorldLit",1);color.setTexture("TppLightmap",mc.gameRenderer.levelLightmap());
        try(var pass=encoder.createRenderPass(colorPass)) {
            var batch=color.batch(encoder,pass);
            for(var item:visible) {
                configure(color,item,camera,main.getDepthTextureView(),false);
                batch.draw(p->{p.enableScissor(item.x,item.y,item.width,item.height);p.draw(3,1,0,0);});
            }
        }
        if(materials!=null) {
            material.prepare();
            configureCommon(material,camera,main.getDepthTextureView());
            try(var pass=encoder.createRenderPass(materials)) {
                var batch=material.batch(encoder,pass);
                for(var item:visible) {
                    if(!item.decal.writesGBuffer())continue;
                    configure(material,item,camera,main.getDepthTextureView(),true);
                    batch.draw(p->{p.enableScissor(item.x,item.y,item.width,item.height);p.draw(3,1,0,0);});
                }
            }
        }
        encoder.submit();
    }
    private void configure(NativeScreenProgram shader,Visible item,CameraSnapshot camera,GpuTextureView depth,boolean gbuffer) {
        var d=item.decal;var mc=Minecraft.getInstance();
        shader.setMatrix("DecalInv",item.inverse);
        shader.setFloat("Opacity",d.opacity());shader.setFloat("AngleFade",d.angleFade());
        shader.setVector("DecalAxis",item.normal.x,item.normal.y,item.normal.z);
        var fallback=whiteView;
        var texture=item.texture;
        if(gbuffer) {
            shader.setTexture("NormalSampler",item.normalTexture);
            shader.setInt("HasNormalMap",d.normalMap()==null?0:1);
            shader.setTexture("TppWaterColor",texture);shader.setInt("TppHasColor",d.texture()==null?0:1);
            shader.setVector("TppTint",d.tint().x,d.tint().y,d.tint().z);
            shader.setVector("DecalRight",item.right.x,item.right.y,item.right.z);shader.setVector("DecalUp",item.up.x,item.up.y,item.up.z);
            shader.setFloat("Roughness",d.roughnessOr(.7f));shader.setFloat("Metallic",d.metallic());
            if(d.nearest()) {
                shader.setTextureNearest("NormalSampler",item.normalTexture);
                shader.setTextureNearest("TppWaterColor",texture);
            }
        } else {
            shader.setTexture("DecalSampler",texture);shader.setInt("HasTexture",d.texture()==null?0:1);
            shader.setVector("Tint",d.tint().x,d.tint().y,d.tint().z);
            shader.setVector("TppLightUV",item.block,item.sky);
            if(d.nearest())shader.setTextureNearest("DecalSampler",texture);
        }
    }
    private void configureCommon(NativeScreenProgram shader,CameraSnapshot camera,GpuTextureView depth) {
        shader.setTexture("DepthSampler",depth);shader.setMatrix("InvViewProj",camera.invViewProj);shader.setInt("ZeroToOne",camera.zeroToOne?1:0);
    }
    private GpuTextureView texture(Identifier id) {
        var tm=Minecraft.getInstance().getTextureManager();if(!ImportedTextures.isImported(id)&&loaded.add(id))VanillaCompat.loadTexture(tm,id);
        return tm.getTexture(id).getTextureView();
    }
    @Override public void close() {if(color!=null)color.close();if(material!=null)material.close();if(whiteView!=null)whiteView.close();if(white!=null)white.close();whiteView=null;white=null;color=material=null;loaded.clear();visible.clear();cache.clear();level=null;}
}
