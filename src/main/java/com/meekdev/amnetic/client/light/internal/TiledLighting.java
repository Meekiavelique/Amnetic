package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.internal.LightBuffer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.ShaderProgram;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.lwjgl.BufferUtils;


public final class TiledLighting {
    private static final int UNIT=14;
    private static final List<Light> SELECTED=new ArrayList<>();
    private static final int[] BOUNDS=new int[4];
    private static final float[] SPOT_BOUNDS=new float[4];
    private static com.mojang.renderpearl.api.buffers.GpuBuffer buffer;
    private static int capacity,columns,rows,limit=1<<20;
    private static final LightClusterLists CLUSTERS=new LightClusterLists();
    private static java.nio.ByteBuffer uploadBytes;
    private static final org.joml.Matrix4f projection=new org.joml.Matrix4f();
    private static final org.joml.Matrix4f viewDepth=new org.joml.Matrix4f();
    private static float[] geometry=new float[0],previousGeometry=new float[0];
    private static int previousWidth=-1,previousHeight=-1,previousCount=-1;
    private static int[] rectangles=new int[0],previousRectangles=new int[0];
    private static int rectangleCount=-1,rectangleColumns,rectangleRows;
    private static boolean attached;
    public static float averageLights;
    private TiledLighting() {}

    public static int pack(LightBuffer lightBuffer,List<Light> lights,Vec3 eye,FrustumIntersection frustum,List<Light> order) {
        var selected=order==null?SELECTED:order;
        int count=lightBuffer.pack(lights,eye,frustum,selected);
        var camera=CameraSnapshot.current();
        if(camera==null || count<=4){columns=0;previousCount=-1;return count;}
        int width=Minecraft.getInstance().gameRenderer.mainRenderTarget().width,height=Minecraft.getInstance().gameRenderer.mainRenderTarget().height;
        if(geometry.length<count*10){geometry=new float[count*10];previousGeometry=new float[count*10];previousCount=-1;}
        for(int i=0;i<count;i++) {
            var light=selected.get(i);int at=i*10;
            geometry[at]=(float)(light.x()-eye.x);geometry[at+1]=(float)(light.y()-eye.y);geometry[at+2]=(float)(light.z()-eye.z);
            geometry[at+3]=influenceRadius(light);
            geometry[at+4]=light.type().id();geometry[at+5]=light.style()==0?0:1;
            boolean spot=light.type().id()==1 && light.style()==0;
            geometry[at+6]=spot?light.dirX():0;geometry[at+7]=spot?light.dirY():0;
            geometry[at+8]=spot?light.dirZ():0;geometry[at+9]=spot?light.cosOuter():0;
        }
        if(previousCount==count && previousWidth==width && previousHeight==height && projection.equals(camera.viewProj) && viewDepth.equals(camera.view)
                && Arrays.equals(geometry,0,count*10,previousGeometry,0,count*10))return count;
        previousCount=count;previousWidth=width;previousHeight=height;projection.set(camera.viewProj);viewDepth.set(camera.view);System.arraycopy(geometry,0,previousGeometry,0,count*10);
        int pixels=32;
        do {columns=(width+pixels-1)/pixels;rows=(height+pixels-1)/pixels;pixels*=2;}while((long)columns*rows*LightDepthSlices.COUNT*(count+2)>limit);
        if(rectangles.length<count*6){rectangles=new int[count*6];previousRectangles=new int[count*6];rectangleCount=-1;}
        for(int index=0;index<count;index++) {
            var light=selected.get(index);
            if(light.type().id()==2 || light.style()!=0)LightTileBounds.full(columns,rows,BOUNDS);
            else if(light.type().id()==1) {
                SpotlightBounds.project(camera.viewProj,(float)(light.x()-eye.x),(float)(light.y()-eye.y),(float)(light.z()-eye.z),
                        light.dirX(),light.dirY(),light.dirZ(),light.range(),light.cosOuter(),width,height,SPOT_BOUNDS);
                BOUNDS[0]=Math.clamp((int)Math.floor(SPOT_BOUNDS[0]*columns)-1,0,columns-1);
                BOUNDS[1]=Math.clamp((int)Math.floor(SPOT_BOUNDS[1]*rows)-1,0,rows-1);
                BOUNDS[2]=Math.clamp((int)Math.floor(SPOT_BOUNDS[2]*columns)+1,0,columns-1);
                BOUNDS[3]=Math.clamp((int)Math.floor(SPOT_BOUNDS[3]*rows)+1,0,rows-1);
            }else {
                float radius=influenceRadius(light);
                LightTileBounds.project(camera.viewProj,(float)(light.x()-eye.x),(float)(light.y()-eye.y),(float)(light.z()-eye.z),radius,columns,rows,BOUNDS);
            }
            int at=index*6;
            System.arraycopy(BOUNDS,0,rectangles,at,4);
            if(light.type().id()==2 || light.style()!=0) {
                rectangles[at+4]=0;rectangles[at+5]=LightDepthSlices.COUNT-1;
            } else {
                var view=camera.view;
                double x=light.x()-eye.x,y=light.y()-eye.y,z=light.z()-eye.z;
                double center=-(view.m02()*x+view.m12()*y+view.m22()*z+view.m32());
                double radius=geometry[index*10+3]*Math.sqrt((double)view.m02()*view.m02()+(double)view.m12()*view.m12()+(double)view.m22()*view.m22());
                rectangles[at+4]=LightDepthSlices.first(center,radius);
                rectangles[at+5]=LightDepthSlices.last(center,radius);
            }
        }
        if(rectangleCount==count && rectangleColumns==columns && rectangleRows==rows
                && Arrays.equals(rectangles,0,count*6,previousRectangles,0,count*6))return count;
        rectangleCount=count;rectangleColumns=columns;rectangleRows=rows;System.arraycopy(rectangles,0,previousRectangles,0,count*6);
        CLUSTERS.build(columns,rows,LightDepthSlices.COUNT,rectangles,count);
        int length=CLUSTERS.length();
        int clusters=columns*rows*LightDepthSlices.COUNT;
        averageLights=CLUSTERS.references()/(float)clusters;
        if(uploadBytes==null || uploadBytes.capacity()<length*4)
            uploadBytes=org.lwjgl.BufferUtils.createByteBuffer(Math.max(length*4,uploadBytes==null?0:uploadBytes.capacity()+uploadBytes.capacity()/2));
        var bytes=uploadBytes.clear().limit(length*4);
        bytes.asIntBuffer().put(CLUSTERS.entries(),0,length);
        if(buffer==null || capacity<length) {
            if(buffer!=null)buffer.close(); capacity=length;
            buffer=com.mojang.blaze3d.systems.RenderSystem.getDevice().createBuffer(()->"Amnetic clustered light indices",
                    com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER|com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_COPY_DST,(long)capacity*4);
        }
        var encoder=com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(buffer.slice(0,(long)length*4),bytes);encoder.submit();
        return count;
    }
    private static float influenceRadius(Light light) {
        float extent=Math.max(Math.max(Math.abs(light.areaW()),Math.abs(light.areaH())),Math.abs(light.tubeLen())*.5F);
        if(light.type().id()==3)extent=Math.max(extent,(float)Math.hypot(light.areaW(),light.areaH()));
        return light.range()+extent;
    }
    public static void bind(com.meekdev.amnetic.client.render.NativeScreenProgram shader,boolean enabled) {
        enabled &= columns>0 && buffer!=null;
        shader.setInt("TileLighting",enabled?1:0);
        if(!enabled)return;
        shader.setTexels("TileLights",buffer.slice());
        var camera=CameraSnapshot.current();var view=camera.view;
        shader.setInt("TileDepthSlices",LightDepthSlices.COUNT);
        shader.setVector("TileViewDepth",-view.m02(),-view.m12(),-view.m22(),-view.m32());
        shader.setInt("TileColumns",columns);shader.setInt("TileRows",rows);
    }
    public static void close(){if(buffer!=null)buffer.close();buffer=null;capacity=columns=0;previousCount=rectangleCount=-1;CLUSTERS.clear();uploadBytes=null;}
}
