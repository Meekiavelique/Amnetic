package com.meekdev.amnetic.client.light.internal;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;


final class AoDistanceRegression {
    static void verify() throws Exception {
        int raw=LightResolveRegression.program(LightResolveRegression.source("shaders/ssao/native/ssao.fsh"));
        int reference=LightResolveRegression.program(LightResolveRegression.expand(Files.readString(Path.of("src/test/resources/ssao/blur-reference.fsh"))));
        int optimized=LightResolveRegression.program(LightResolveRegression.source("shaders/ssao/native/blur.fsh"));
        int fbo=glGenFramebuffers(),vao=glGenVertexArrays();int[] textures=new int[5];glGenTextures(textures);
        float maximum=0;int differences=0,channels=0;
        try {
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glBindVertexArray(vao);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
            for(int scene=0;scene<36;scene++) {
                int width=scene%2==0?63:96,height=scene%2==0?41:60;boolean zero=scene%3==0;
                var view=new Matrix4f().rotateX(scene*.13F).rotateY(scene*.21F);
                var projection=new Matrix4f().perspective(.9F+scene*.01F,width/(float)height,.05F,200,zero);
                var vp=new Matrix4f(projection).mul(view);var inverse=new Matrix4f(vp).invert();
                float[] depth=new float[width*height],normal=new float[width*height*4],ao=new float[width*height*4];
                for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                    int at=x+y*width;
                    float z=-3F-(x<width/2?0:2)-(float)Math.sin(x*.3F)*.25F;
                    var clip=projection.transform(new Vector4f(0,0,z,1));
                    depth[at]=x>width-4?1:(zero?clip.z/clip.w:clip.z/clip.w*.5F+.5F);
                    normal[at*4+(y<height/2?2:1)]=scene%4==0?0:1;normal[at*4+3]=1;
                    float value=scene%6==0?1:scene%6==1?0:scene%6==2?.63F:(x*17+y*11)%13/12F;
                    ao[at*4]=ao[at*4+1]=ao[at*4+2]=value;ao[at*4+3]=1;
                }
                for(int unit=0;unit<5;unit++) {
                    glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,textures[unit]);
                    int format=unit==1 || unit==4?GL_RED:GL_RGBA;
                    int internal=unit==0 || unit==3?GL_RGBA8:unit==1 || unit==4?GL_R32F:GL_RGBA16F;
                    glTexImage2D(GL_TEXTURE_2D,0,internal,width,height,0,format,GL_FLOAT,unit==1?depth:unit==2?normal:unit==3?ao:null);
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,scene%4<2?GL_NEAREST:GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,scene%4<2?GL_NEAREST:GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
                }
                glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[0],0);
                glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_TEXTURE_2D,textures[4],0);
                glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0,GL_COLOR_ATTACHMENT1});glReadBuffer(GL_COLOR_ATTACHMENT0);glViewport(0,0,width,height);
                glUseProgram(raw);glUniform1i(glGetUniformLocation(raw,"DepthSampler"),1);glUniform1i(glGetUniformLocation(raw,"GNormalSampler"),2);
                glUniform1i(glGetUniformLocation(raw,"HasGBuffer"),scene%2);glUniform1i(glGetUniformLocation(raw,"ZeroToOne"),zero?1:0);
                glUniformMatrix4fv(glGetUniformLocation(raw,"ViewProj"),false,vp.get(new float[16]));glUniformMatrix4fv(glGetUniformLocation(raw,"InvViewProj"),false,inverse.get(new float[16]));glUniformMatrix4fv(glGetUniformLocation(raw,"View"),false,view.get(new float[16]));
                glUniform1f(glGetUniformLocation(raw,"Radius"),.84F);glUniform1f(glGetUniformLocation(raw,"Power"),1.18F);
                glDrawArrays(GL_TRIANGLES,0,3);
                glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_TEXTURE_2D,0,0);glDrawBuffer(GL_COLOR_ATTACHMENT0);
                float[][] result={new float[width*height*4],new float[width*height*4]};
                for(int mode=0;mode<2;mode++) {
                    int p=mode==0?reference:optimized;glUseProgram(p);
                    glUniform1i(glGetUniformLocation(p,"AoSampler"),3);glUniform1i(glGetUniformLocation(p,"DepthSampler"),1);glUniform1i(glGetUniformLocation(p,"GNormalSampler"),2);
                    glUniform1i(glGetUniformLocation(p,"HasGBuffer"),scene%2);glUniform1i(glGetUniformLocation(p,"ZeroToOne"),zero?1:0);
                    glUniform1i(glGetUniformLocation(p,"DistanceSampler"),4);glUniform1i(glGetUniformLocation(p,"PreparedDistance"),1);
                    glUniformMatrix4fv(glGetUniformLocation(p,"InvViewProj"),false,inverse.get(new float[16]));
                    glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,width,height,GL_RGBA,GL_FLOAT,result[mode]);
                }
                for(int i=0;i<result[0].length;i++) {
                    float error=Math.abs(result[0][i]-result[1][i]);maximum=Math.max(maximum,error);channels++;if(error>0)differences++;
                    if(!Float.isFinite(error) || error>1.01F/255)throw new AssertionError("Prepared AO distance changed an edge: "+error+", scene="+scene);
                }
            }
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("AO distance comparison GL error");
            if(differences>channels/1000)throw new AssertionError("Prepared distances changed too many stored channels: "+differences+"/"+channels);
            System.out.println("AO distance reuse: 36 production MRT/blur scenes, rotated cameras, odd sizes and borders; maximum channel error="+maximum+", changed channels="+differences+"/"+channels);
        }finally {
            glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);glActiveTexture(GL_TEXTURE0);glDeleteProgram(raw);glDeleteProgram(reference);glDeleteProgram(optimized);glDeleteFramebuffers(fbo);glDeleteVertexArrays(vao);glDeleteTextures(textures);
        }
    }
}
