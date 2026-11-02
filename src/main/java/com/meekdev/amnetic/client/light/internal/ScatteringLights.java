package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.internal.LightBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;


public final class ScatteringLights {
    private static final List<Light> SELECTED = new ArrayList<>();
    public static int selected, scattering;
    private static final float[] BOUNDS=new float[4],TEMP=new float[4];

    private ScatteringLights() { }

    public static int pack(LightBuffer buffer, List<Light> lights, Vec3 eye, FrustumIntersection frustum) {
        scattering = buffer.pack(lights, eye, frustum, SELECTED, light -> light.godray()>0);
        selected = buffer.selectionCount();
        bounds(eye);
        return scattering;
    }
    private static void bounds(Vec3 eye) {
        var camera=com.meekdev.amnetic.client.render.CameraSnapshot.current();
        if(camera==null || scattering==0){SpotlightBounds.full(BOUNDS);return;}
        var target=net.minecraft.client.Minecraft.getInstance().gameRenderer.mainRenderTarget();
        BOUNDS[0]=BOUNDS[1]=1;BOUNDS[2]=BOUNDS[3]=0;
        for(var light:SELECTED) {
            if(light.type().id()!=1){SpotlightBounds.full(BOUNDS);return;}
            SpotlightBounds.project(camera.viewProj,(float)(light.x()-eye.x),(float)(light.y()-eye.y),(float)(light.z()-eye.z),
                    light.dirX(),light.dirY(),light.dirZ(),light.range(),light.cosOuter(),target.width,target.height,TEMP);
            BOUNDS[0]=Math.min(BOUNDS[0],TEMP[0]);BOUNDS[1]=Math.min(BOUNDS[1],TEMP[1]);
            BOUNDS[2]=Math.max(BOUNDS[2],TEMP[2]);BOUNDS[3]=Math.max(BOUNDS[3],TEMP[3]);
        }
    }
    public static void bind(com.meekdev.amnetic.client.render.NativeScreenProgram shader,boolean enabled) {
        shader.setInt("ScatteringClip",enabled?1:0);
        if(enabled)shader.setVector("ScatteringRect",BOUNDS[0],BOUNDS[1],BOUNDS[2],BOUNDS[3]);
    }
}
