package com.meekdev.amnetic.client.light.internal;

import org.joml.Matrix4fc;
import org.joml.Vector3f;


public final class SpotlightBounds {
    private static final class Scratch { final Vector3f axis=new Vector3f(),u=new Vector3f(),v=new Vector3f(); }
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    private SpotlightBounds() {}

    public static void project(Matrix4fc matrix,float x,float y,float z,float dx,float dy,float dz,
                               float range,float cosine,int width,int height,float[] bounds) {
        if(cosine<=.001F || range<=0){full(bounds);return;}
        var s=SCRATCH.get();s.axis.set(dx,dy,dz);
        if(s.axis.lengthSquared()<1e-8F){full(bounds);return;}
        s.axis.normalize();
        s.u.set(Math.abs(s.axis.y)<.9F?0:1,Math.abs(s.axis.y)<.9F?1:0,0).cross(s.axis).normalize();
        s.v.set(s.axis).cross(s.u);
        float radius=range*(float)Math.sqrt(Math.max(0,1-cosine*cosine))/cosine;
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
        for(int corner=0;corner<5;corner++) {
            float a=x,b=y,c=z;
            if(corner>0) {
                float u=((corner-1&1)==0?-1:1)*radius,v=((corner-1&2)==0?-1:1)*radius;
                a+=s.axis.x*range+s.u.x*u+s.v.x*v;b+=s.axis.y*range+s.u.y*u+s.v.y*v;c+=s.axis.z*range+s.u.z*u+s.v.z*v;
            }
            float w=matrix.m03()*a+matrix.m13()*b+matrix.m23()*c+matrix.m33();
            if(w<=.0001F || !Float.isFinite(w)){full(bounds);return;}
            float px=(matrix.m00()*a+matrix.m10()*b+matrix.m20()*c+matrix.m30())/w;
            float py=(matrix.m01()*a+matrix.m11()*b+matrix.m21()*c+matrix.m31())/w;
            if(!Float.isFinite(px) || !Float.isFinite(py)){full(bounds);return;}
            minX=Math.min(minX,px);minY=Math.min(minY,py);maxX=Math.max(maxX,px);maxY=Math.max(maxY,py);
        }
        bounds[0]=clamp(minX*.5F+.5F-2F/width,0,1);bounds[1]=clamp(minY*.5F+.5F-2F/height,0,1);
        bounds[2]=clamp(maxX*.5F+.5F+2F/width,0,1);bounds[3]=clamp(maxY*.5F+.5F+2F/height,0,1);
    }
    public static void full(float[] bounds){bounds[0]=bounds[1]=0;bounds[2]=bounds[3]=1;}
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
