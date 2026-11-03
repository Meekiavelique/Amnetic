package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.light.internal.LightClusterLists;
import com.meekdev.amnetic.client.light.internal.LightDepthSlices;
import com.meekdev.amnetic.client.light.internal.LightTileBounds;
import com.meekdev.amnetic.client.light.internal.SpotlightBounds;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;


final class TiledLightingRegression {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void verify()throws Exception {
        verifyCameraPlaneCrossings();
        String source=LightResolveRegression.source("shaders/light/deferred.fsh");
        String loop=source.substring(source.indexOf("int tileBase="),source.indexOf("// scene is already"));
        String main="""
                uniform mat4 TestInverse;uniform float TestDepth;
                out vec4 TestResult;
                void main(){
                    vec4 ray=TestInverse*vec4(vUV*2-1,0,1);
                    vec3 fragPos=ray.xyz/ray.w;fragPos*=TestDepth/-fragPos.z;
                    vec3 N=vec3(0,0,1),V=normalize(-fragPos);
                    float rough=.65,f0=.04,sunShadow=0;
                    vec3 radiance=vec3(0),specular=vec3(0);
                """+loop+"TestResult=vec4(radiance+specular,1);}";
        int program=LightResolveRegression.program(source.substring(0,source.indexOf("void main()"))+main);
        int fbo=glGenFramebuffers(),vao=glGenVertexArrays();int[] textures=new int[3],buffers=new int[2];glGenTextures(textures);glGenBuffers(buffers);
        try {
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[0]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,96,60,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[0],0);glDrawBuffer(GL_COLOR_ATTACHMENT0);glReadBuffer(GL_COLOR_ATTACHMENT0);
            glBindVertexArray(vao);glViewport(0,0,96,60);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glUseProgram(program);
            glUniform1i(glGetUniformLocation(program,"LightTexels"),11);glUniform1i(glGetUniformLocation(program,"TileLights"),14);
            int count=48,columns=12,rows=8,slices=LightDepthSlices.COUNT;
            glUniform1i(glGetUniformLocation(program,"LightCount"),count);glUniform1i(glGetUniformLocation(program,"TileColumns"),columns);glUniform1i(glGetUniformLocation(program,"TileRows"),rows);
            glUniform1i(glGetUniformLocation(program,"TileDepthSlices"),slices);
            glUniform4f(glGetUniformLocation(program,"TileViewDepth"),0,0,-1,0);
            var random=new Random(51071);int[] bounds=new int[4];float maxError=0;long candidates=0;
            for(int scene=0;scene<24;scene++) {
                var projection=new Matrix4f().perspective((float)Math.toRadians(45+scene*2),1.6F,.05F,100).rotateY((scene%3-1)*.15F).rotateX((scene%5-2)*.04F);
                glUniformMatrix4fv(glGetUniformLocation(program,"TestInverse"),false,new Matrix4f(projection).invert().get(new float[16]));
                glUniform1f(glGetUniformLocation(program,"TestDepth"),3+scene*2);
                float[] lights=new float[count*32];int[] regions=new int[count*6];
                for(int light=0;light<count;light++) {
                    int base=light*32;
                    float x=random.nextFloat()*20-10,y=random.nextFloat()*16-8,z=-2-random.nextFloat()*55,radius=3+random.nextFloat()*8;
                    lights[base]=x;lights[base+1]=y;lights[base+2]=z;lights[base+3]=radius;
                    lights[base+4]=.3F+random.nextFloat()*.7F;lights[base+5]=.3F+random.nextFloat()*.7F;lights[base+6]=.3F+random.nextFloat()*.7F;lights[base+7]=1.28F;
                    lights[base+10]=-1;lights[base+19]=1;lights[base+23]=-1;
                    int type=switch(light%6){case 1->1;case 2->3;case 3->4;case 4->5;default->0;};
                    float extent=0;
                    lights[base+11]=type;
                    if(type>=3){lights[base+16]=2;lights[base+17]=1.5F;lights[base+18]=3;lights[base+20]=1;extent=type==3?2.5F:2;}
                    LightTileBounds.project(projection,x,y,z,radius+extent,columns,rows,bounds);
                    boolean spot=type==1;
                    if(spot) {
                        lights[base+11]=1;lights[base+12]=.97F;lights[base+13]=.90F;
                        float[] rectangle=new float[4];
                        SpotlightBounds.project(projection,x,y,z,0,0,-1,radius,.9F,96,60,rectangle);
                        bounds[0]=clamp((int)Math.floor(rectangle[0]*columns)-1,0,columns-1);
                        bounds[1]=clamp((int)Math.floor(rectangle[1]*rows)-1,0,rows-1);
                        bounds[2]=clamp((int)Math.floor(rectangle[2]*columns)+1,0,columns-1);
                        bounds[3]=clamp((int)Math.floor(rectangle[3]*rows)+1,0,rows-1);
                    }
                    for(int sample=0;sample<40;sample++) {
                        float dx=(random.nextFloat()*2-1)*radius,dy=(random.nextFloat()*2-1)*radius,dz=(random.nextFloat()*2-1)*radius;
                        if(spot && (dx*dx+dy*dy+dz*dz>radius*radius || -dz/Math.sqrt(dx*dx+dy*dy+dz*dz)<.9))continue;
                        var p=new Vector4f(x+dx,y+dy,z+dz,1).mul(projection);
                        if(p.w<=0 || Math.abs(p.x)>p.w || Math.abs(p.y)>p.w)continue;
                        int tx=clamp((int)((p.x/p.w*.5F+.5F)*columns),0,columns-1),ty=clamp((int)((p.y/p.w*.5F+.5F)*rows),0,rows-1);
                        require(tx>=bounds[0] && tx<=bounds[2] && ty>=bounds[1] && ty<=bounds[3],"Light culling clipped a visible influence volume");
                    }
                    if(light==count-1){lights[base+11]=2;LightTileBounds.full(columns,rows,bounds);}
                    if(light==count-2){lights[base+27]=5;LightTileBounds.full(columns,rows,bounds);}
                    System.arraycopy(bounds,0,regions,light*6,4);
                    regions[light*6+4]=LightDepthSlices.first(-z,radius+extent);
                    regions[light*6+5]=LightDepthSlices.last(-z,radius+extent);
                    if(light>=count-2){regions[light*6+4]=0;regions[light*6+5]=slices-1;}
                }
                var clusters=new LightClusterLists();clusters.build(columns,rows,slices,regions,count);
                int[] tiles=Arrays.copyOf(clusters.entries(),clusters.length());candidates+=clusters.length()-2*columns*rows*slices;
                glActiveTexture(GL_TEXTURE11);glBindTexture(GL_TEXTURE_BUFFER,textures[1]);glBindBuffer(GL_TEXTURE_BUFFER,buffers[0]);glBufferData(GL_TEXTURE_BUFFER,lights,GL_STREAM_DRAW);glTexBuffer(GL_TEXTURE_BUFFER,GL_RGBA32F,buffers[0]);
                glActiveTexture(GL_TEXTURE14);glBindTexture(GL_TEXTURE_BUFFER,textures[2]);glBindBuffer(GL_TEXTURE_BUFFER,buffers[1]);glBufferData(GL_TEXTURE_BUFFER,tiles,GL_STREAM_DRAW);glTexBuffer(GL_TEXTURE_BUFFER,GL_R32I,buffers[1]);
                float[][] pixels={new float[96*60*4],new float[96*60*4]};
                for(int mode=0;mode<2;mode++){glUniform1i(glGetUniformLocation(program,"TileLighting"),mode);glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,96,60,GL_RGBA,GL_FLOAT,pixels[mode]);}
                float energy=0;
                for(int i=0;i<pixels[0].length;i++){energy+=i%4==3?0:pixels[0][i];float error=Math.abs(pixels[0][i]-pixels[1][i]);maxError=Math.max(maxError,error);require(error<1e-6,"Tile lists changed local lighting: "+error);}
                require(energy>.001,"Lighting comparison rendered only black");
            }
            require(glGetError()==GL_NO_ERROR,"Tiled light comparison GL error");
            System.out.println("Tiled lighting: 24 scenes, 48 lights, all six shapes, styled lights and 46,080 projected samples; maximum float error="+maxError+", average candidates="+candidates/(24F*columns*rows*slices));
        } finally {glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);glActiveTexture(GL_TEXTURE0);glDeleteProgram(program);glDeleteFramebuffers(fbo);glDeleteVertexArrays(vao);glDeleteTextures(textures);glDeleteBuffers(buffers);}
    }
    private static void verifyCameraPlaneCrossings() {
        var random=new Random(9328);int[] bounds=new int[4];int visible=0;
        for(int scene=0;scene<2000;scene++) {
            var projection=new Matrix4f().perspective(.7F+random.nextFloat(),1.6F,.05F,200)
                    .rotateX(random.nextFloat()-.5F).rotateY(random.nextFloat()*3-1.5F);
            float x=random.nextFloat()*24-12,y=random.nextFloat()*16-8,z=random.nextFloat()*16-12,radius=.1F+random.nextFloat()*8;
            LightTileBounds.project(projection,x,y,z,radius,60,34,bounds);
            for(int sample=0;sample<100;sample++) {
                var point=new Vector4f(x+(random.nextFloat()*2-1)*radius,y+(random.nextFloat()*2-1)*radius,z+(random.nextFloat()*2-1)*radius,1).mul(projection);
                if(point.w<.05F || Math.abs(point.x)>point.w || Math.abs(point.y)>point.w)continue;
                int tx=clamp((int)((point.x/point.w*.5F+.5F)*60),0,59),ty=clamp((int)((point.y/point.w*.5F+.5F)*34),0,33);
                require(tx>=bounds[0] && tx<=bounds[2] && ty>=bounds[1] && ty<=bounds[3],"Camera-plane light clipping removed a visible point");visible++;
            }
        }
        require(visible>10000,"Camera-plane regression did not exercise visible bounds");
        System.out.println("Light bounds: 200,000 samples across rotated, offscreen and camera-plane-crossing boxes; visible samples="+visible);
    }
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
}
