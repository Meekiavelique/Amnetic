package com.meekdev.amnetic.client.light.internal;

import org.joml.Matrix4fc;


public final class LightTileBounds {
    private static final double MIN_W = 1e-6;
    private static final ThreadLocal<Projection> PROJECTION = ThreadLocal.withInitial(Projection::new);
    private LightTileBounds() {}
    public static void project(Matrix4fc matrix,float x,float y,float z,float radius,int columns,int rows,int[] bounds) {
        Projection projected = PROJECTION.get();
        projected.reset();
        for(int corner=0;corner<8;corner++) {
            double a=x+((corner&1)==0?-(double)radius:radius),b=y+((corner&2)==0?-(double)radius:radius),c=z+((corner&4)==0?-(double)radius:radius);
            int at=corner*3;
            projected.vertices[at]=matrix.m00()*a+matrix.m10()*b+matrix.m20()*c+matrix.m30();
            projected.vertices[at+1]=matrix.m01()*a+matrix.m11()*b+matrix.m21()*c+matrix.m31();
            double w=matrix.m03()*a+matrix.m13()*b+matrix.m23()*c+matrix.m33();
            projected.vertices[at+2]=w;
            if(!Double.isFinite(w) || !Double.isFinite(projected.vertices[at]) || !Double.isFinite(projected.vertices[at+1])){full(columns,rows,bounds);return;}
            if(w>=MIN_W)projected.add(projected.vertices[at],projected.vertices[at+1],w);
        }
        for(int corner=0;corner<8;corner++)for(int axis=1;axis<=4;axis<<=1) {
            if((corner&axis)!=0)continue;
            int a=corner*3,b=(corner|axis)*3;
            double wa=projected.vertices[a+2],wb=projected.vertices[b+2];
            if((wa>=MIN_W)==(wb>=MIN_W))continue;
            double t=(MIN_W-wa)/(wb-wa);
            projected.add(projected.vertices[a]+t*(projected.vertices[b]-projected.vertices[a]),
                    projected.vertices[a+1]+t*(projected.vertices[b+1]-projected.vertices[a+1]),MIN_W);
        }
        if(projected.minX>1 || projected.maxX< -1 || projected.minY>1 || projected.maxY< -1) {
            bounds[0]=bounds[1]=0;bounds[2]=bounds[3]=-1;return;
        }
        bounds[0]=Math.max(0,(int)Math.floor((Math.max(-1,projected.minX)*.5+.5)*columns)-1);
        bounds[1]=Math.max(0,(int)Math.floor((Math.max(-1,projected.minY)*.5+.5)*rows)-1);
        bounds[2]=Math.min(columns-1,(int)Math.floor((Math.min(1,projected.maxX)*.5+.5)*columns)+1);
        bounds[3]=Math.min(rows-1,(int)Math.floor((Math.min(1,projected.maxY)*.5+.5)*rows)+1);
    }
    public static void full(int columns,int rows,int[] bounds){bounds[0]=bounds[1]=0;bounds[2]=columns-1;bounds[3]=rows-1;}

    private static final class Projection {
        final double[] vertices = new double[24];
        double minX,minY,maxX,maxY;
        void reset(){minX=minY=Double.POSITIVE_INFINITY;maxX=maxY=Double.NEGATIVE_INFINITY;}
        void add(double x,double y,double w) {
            x/=w;y/=w;
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        }
    }
}
