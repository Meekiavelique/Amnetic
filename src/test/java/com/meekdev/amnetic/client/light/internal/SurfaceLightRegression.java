package com.meekdev.amnetic.client.light.internal;

import org.joml.Matrix4f;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;


final class SurfaceLightRegression {
    static void verify() throws Exception {
        String main = """
                in vec2 vUV;out vec4 result;
                void main(){
                    vec3 p=vec3((vUV*2-1)*8,-8);
                    vec3 n=normalize(vec3(sin(gl_FragCoord.x*.3),cos(gl_FragCoord.y*.2),1));
                    float rough=float(int(gl_FragCoord.x)%5)*.25;
                    vec3 specular=vec3(0),diffuse=vec3(0);float sun=0;
                    for(int i=0;i<6;i++)diffuse+=lightContribution(i,p,n,normalize(-p),rough,.04,specular,sun);
                    result=vec4(diffuse+specular,sun);
                }
                """;
        int[] programs = {
                LightResolveRegression.program("#version 410\n"+LightResolveRegression.expand(Files.readString(Path.of("src/test/resources/lighting/lighting-reference.glsl")))+main),
                LightResolveRegression.program("#version 410\n"+LightResolveRegression.source("shaders/light/lighting.glsl")+main)
        };
        int fbo=glGenFramebuffers(),vao=glGenVertexArrays(),buffer=glGenBuffers();
        int[] textures=new int[3];glGenTextures(textures);
        try {
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[0]);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,96,60,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[0],0);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);glReadBuffer(GL_COLOR_ATTACHMENT0);glBindVertexArray(vao);glViewport(0,0,96,60);
            glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
            glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,textures[1]);
            float[] depth=new float[96*60];Arrays.fill(depth,.985F);
            glTexImage2D(GL_TEXTURE_2D,0,GL_R32F,96,60,0,GL_RED,GL_FLOAT,depth);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            var projection=new Matrix4f().perspective(1.2F,1.6F,.05F,200);
            float maximum=0;
            for(int scene=0;scene<48;scene++) {
                float[] lights=new float[6*32];
                for(int type=0;type<6;type++) {
                    int at=type*32;lights[at]=(type-2)*2F;lights[at+1]=(scene%5-2)*2;lights[at+2]=-2;lights[at+3]=16;
                    lights[at+4]=.7F;lights[at+5]=.3F;lights[at+6]=1;lights[at+7]=1.3F;
                    lights[at+8]=.2F;lights[at+9]=-.3F;lights[at+10]=-1;lights[at+11]=type;
                    lights[at+12]=.97F;lights[at+13]=.7F;lights[at+14]=scene%4;lights[at+15]=2.3F;
                    lights[at+16]=2;lights[at+17]=1;lights[at+18]=3;lights[at+19]=(scene%4)/3F;
                    lights[at+20]=1;lights[at+23]=scene%2==0?0:-1;lights[at+25]=scene%6;
                }
                glActiveTexture(GL_TEXTURE11);glBindTexture(GL_TEXTURE_BUFFER,textures[2]);glBindBuffer(GL_TEXTURE_BUFFER,buffer);
                glBufferData(GL_TEXTURE_BUFFER,lights,GL_STREAM_DRAW);glTexBuffer(GL_TEXTURE_BUFFER,GL_RGBA32F,buffer);
                float[][] output={new float[96*60*4],new float[96*60*4]};
                for(int mode=0;mode<2;mode++) {
                    int p=programs[mode];glUseProgram(p);glUniform1i(glGetUniformLocation(p,"LightTexels"),11);
                    glUniform1i(glGetUniformLocation(p,"DepthSampler"),1);glUniform1i(glGetUniformLocation(p,"TestOcclusion"),scene%3==0?1:0);
                    glUniform1i(glGetUniformLocation(p,"ContactSteps"),scene%2==0?4:0);
                    glUniform1f(glGetUniformLocation(p,"ContactDistance"),.5F);glUniform1f(glGetUniformLocation(p,"ContactThickness"),.2F);
                    glUniform1f(glGetUniformLocation(p,"ShadowFadeStart"),32);glUniform1f(glGetUniformLocation(p,"ShadowFadeEnd"),48);glUniform1f(glGetUniformLocation(p,"SunShadowDistance"),64);
                    glUniformMatrix4fv(glGetUniformLocation(p,"ViewProj"),false,projection.get(new float[16]));glUniformMatrix4fv(glGetUniformLocation(p,"InvViewProj"),false,new Matrix4f(projection).invert().get(new float[16]));
                    glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,96,60,GL_RGBA,GL_FLOAT,output[mode]);
                }
                for(int i=0;i<output[0].length;i++) {
                    float error=Math.abs(output[0][i]-output[1][i]);maximum=Math.max(maximum,error);
                    if(!Float.isFinite(error) || error>1e-6F)throw new AssertionError("Surface light changed: "+error+", scene="+scene);
                }
            }
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("Surface light comparison GL error");
            System.out.println("Surface lighting: 48 scenes, all six shapes, matte/glossy, contact shadows and shadow-strength fades; maximum float error="+maximum);
        } finally {
            glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);glActiveTexture(GL_TEXTURE0);
            for(int p:programs)glDeleteProgram(p);glDeleteFramebuffers(fbo);glDeleteVertexArrays(vao);glDeleteTextures(textures);glDeleteBuffers(buffer);
        }
    }
}
