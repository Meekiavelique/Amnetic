package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.light.internal.SpotlightBounds;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL40;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.regex.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;


final class LightResolveRegression {
    private static boolean realSpotShadows;
    static String source(String path)throws Exception {
        if(path.endsWith("styles.glsl"))return "const int AMNETIC_STYLE_POINT=0,AMNETIC_STYLE_SHADE=1;void amneticLightStyle(int a,int b,inout vec3 p,inout float f,inout vec3 c,vec3 s,vec3 d){}";
        if(path.endsWith("shadowmap.glsl") && !realSpotShadows)return """
                uniform float ShadowFadeStart,ShadowFadeEnd,SunShadowDistance;
                uniform int TestOcclusion;
                uniform int ShadowActive,ShadowGridX;
                uniform float ShadowTileSize,ShadowAtlasSize,ShadowBias;
                uniform mat4 SpotViewProj[16];
                uniform sampler2D SpotShadowAtlas;
                vec3 shadowVisibility(int r,vec3 p,vec3 n,vec3 l,float d){return vec3(TestOcclusion==1?0:1);}
                float shadowVisibilityCheap(int r,vec3 p,vec3 l,float d){return TestOcclusion==1?0:1;}
                """;
        if(path.endsWith("custom_ladder.glsl"))return """
                struct GBufferSample {vec3 albedo;vec3 normal;vec3 position;float rough;float metallic;vec3 outputColor;vec3 light;float time;};
                vec3 shadeCustomMaterial(int id,GBufferSample s,out bool handled){handled=false;return s.outputColor;}
                """;
        String value;
        try(var in=LightResolveRegression.class.getResourceAsStream("/assets/amnetic/"+path)) {
            require(in!=null,"Missing production shader "+path);value=new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
        return expand(value);
    }
    static String expand(String value)throws Exception {
        var m=Pattern.compile("#include \"amnetic:([^\"]+)\"").matcher(value);
        var out=new StringBuilder();
        while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(source(m.group(1))));
        m.appendTail(out);return out.toString();
    }
    private static int shader(int type,String source) {
        int s=glCreateShader(type);
        glShaderSource(s,source);
        glCompileShader(s);
        require(glGetShaderi(s,GL_COMPILE_STATUS)!=0,glGetShaderInfoLog(s));return s;
    }
    static int program(String fragment) {
        int v=shader(GL_VERTEX_SHADER,"#version 410\nout vec2 vUV;void main(){vec2 p[3]=vec2[3](vec2(-1,-1),vec2(3,-1),vec2(-1,3));gl_Position=vec4(p[gl_VertexID],0,1);vUV=p[gl_VertexID]*.5+.5;}");
        int f=shader(GL_FRAGMENT_SHADER,fragment);
        int p=glCreateProgram();
        glAttachShader(p,v);
        glAttachShader(p,f);
        glLinkProgram(p);
        require(glGetProgrami(p,GL_LINK_STATUS)!=0,glGetProgramInfoLog(p));glDeleteShader(v);glDeleteShader(f);return p;
    }
    static void verify()throws Exception {
        TiledLightingRegression.verify();
        SurfaceLightRegression.verify();
        glDeleteProgram(program(source("shaders/light/deferred.fsh")));
        glDeleteProgram(program(source("shaders/light/volume.fsh")));
        String common=source("shaders/light/lighting.glsl");
        int resolve=program("#version 410\n"+common+"""
                out vec4 result;uniform float BaseStrength;
                void main(){
                    float energy=pow(4.0,floor(gl_FragCoord.x));
                    if(gl_FragCoord.x<1.0)energy=0.0;
                    vec3 material=vec3(.65,.12,.05);
                    vec3 base=material*BaseStrength;
                    vec3 color=resolveSurface(base,1.0,material,surfaceRadiance(material,vec3(energy),vec3(0),0.0));
                    result=vec4(color,1);
                }
                """);
        String deferred=source("shaders/light/deferred.fsh");
        deferred=deferred.substring(0,deferred.indexOf("void main()"));
        int rays=program(deferred+"out vec4 TestResult;void main(){TestResult=vec4(volumetric(vec3(0,0,-8)),1);}");
        int dark=program("#version 410\n"+common+"""
                out vec4 result;uniform float Energy;
                void main(){float levels[5]=float[5](0.0,.08,.20,.40,.65);
                    vec3 material=vec3(levels[int(floor(gl_FragCoord.x))]);
                    result=vec4(resolveSurface(vec3(0),1.0,material,surfaceRadiance(material,vec3(Energy),vec3(0),0)),1);}
                """);
        int fbo=glGenFramebuffers();
        int vao=glGenVertexArrays();
        int buffer=glGenBuffers();
        int[] textures=new int[3];
        glGenTextures(textures);
        try {
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[0]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,5,1,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[0],0);glDrawBuffer(GL_COLOR_ATTACHMENT0);glReadBuffer(GL_COLOR_ATTACHMENT0);
            glBindVertexArray(vao);glViewport(0,0,5,1);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
            glUseProgram(resolve);
            glUniform1f(glGetUniformLocation(resolve,"BaseStrength"),.02F);
            glDrawArrays(GL_TRIANGLES,0,3);
            float[] pixels=new float[20];
            glReadPixels(0,0,5,1,GL_RGBA,GL_FLOAT,pixels);
            float expectedRatio=linear(.12F)/linear(.65F);
            float previous=0;
            for(int i=0;i<5;i++) {
                float r=pixels[i*4];
                float g=pixels[i*4+1];
                float b=pixels[i*4+2];
                require(Float.isFinite(r) && r>=previous && r<=1.014F && b>=0,"Local light energy overflowed or became non-monotonic");
                if(i>0) {
                    float redEnergy=linear(r)-linear(.65F*.02F);
                    float greenEnergy=linear(g)-linear(.12F*.02F);
                    require(Math.abs(greenEnergy/redEnergy-expectedRatio)<1e-4,"Neutral lights bleached linear material hue");
                }
                previous=r;
            }
            require(Math.abs(pixels[0]-.65F*.02F)<1e-6,"Unlit scene changed");
            glUniform1f(glGetUniformLocation(resolve,"BaseStrength"),1);glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,5,1,GL_RGBA,GL_FLOAT,pixels);
            require(Math.abs(pixels[0]-.65F)<1e-6 && pixels[4]>.70F,"Amnetic lamps disappeared on already-lit material");
            glUseProgram(dark);
            for(float energy:new float[]{0,.125F,1,1024}) {
                glUniform1f(glGetUniformLocation(dark,"Energy"),energy);glDrawArrays(GL_TRIANGLES,0,3);
                glReadPixels(0,0,5,1,GL_RGBA,GL_FLOAT,pixels);
                require(pixels[0]==0,"Local light created a global ambient floor on black material");
                if(energy==0)for(int i=0;i<5;i++)require(pixels[i*4]==0,"Dark material glows without a light");
                if(energy==.125F)require(pixels[8]>.045F,"Steel remains unreadable under a weak local lamp without vanilla lighting: "+pixels[8]);
                if(energy>0) {
                    for(int i=1;i<5;i++)require(pixels[i*4]>pixels[(i-1)*4],"Local lighting flattened texture contrast");
                    require(Math.abs(linear(pixels[8])/linear(pixels[16])-linear(.2F)/linear(.65F))<1e-4,"Illumination shoulder bleached dark texels");
                }
            }
            System.out.println("Dark materials: zero vanilla light, readable weak-lamp steel, black stays black, texture contrast preserved through extreme light energy.");
            glUseProgram(rays);
            float[] light={0,0,-3,6, 1,.8F,.5F,4, 0,0,-1,1, .98F,.8F,0,1, 0,0,0,1, 1,0,0,-1, 0,0,1,0, 32,.4F,.54F,1};
            glBindBuffer(GL_TEXTURE_BUFFER,buffer);glBufferData(GL_TEXTURE_BUFFER,light,GL_STATIC_DRAW);
            glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_BUFFER,textures[1]);glTexBuffer(GL_TEXTURE_BUFFER,GL_RGBA32F,buffer);
            glUniform1i(glGetUniformLocation(rays,"LightTexels"),1);glUniform1i(glGetUniformLocation(rays,"LightCount"),1);
            glUniform1i(glGetUniformLocation(rays,"DepthSampler"),2);glUniform1i(glGetUniformLocation(rays,"CookieSampler"),2);
            glUniform1f(glGetUniformLocation(rays,"VolumetricAniso"),.54F);
            glUniform1f(glGetUniformLocation(rays,"TemporalOffset"),-1);
            float control=0;
            for(int test=0;test<5;test++) {
                glUniform1i(glGetUniformLocation(rays,"VolumetricSteps"),test==0?0:32);
                glUniform1f(glGetUniformLocation(rays,"VolumetricStrength"),test==2?0:1);
                glUniform1f(glGetUniformLocation(rays,"VolumetricDensity"),test==3?0:.2F);
                glUniform1i(glGetUniformLocation(rays,"VolumetricShadows"),1);
                glUniform1i(glGetUniformLocation(rays,"TestOcclusion"),test==4?1:0);
                glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,5,1,GL_RGBA,GL_FLOAT,pixels);
                if(test==1){control=pixels[0];require(control>1e-4,"Enabled godrays are invisible");}
                else require(Math.abs(pixels[0])<1e-7,"Disabled/occluded godrays still contribute, case "+test);
            }
            light[3]=0;light[11]=2;
            glBindBuffer(GL_TEXTURE_BUFFER,buffer);glBufferData(GL_TEXTURE_BUFFER,light,GL_STATIC_DRAW);
            glUniform1i(glGetUniformLocation(rays,"TestOcclusion"),0);
            glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,5,1,GL_RGBA,GL_FLOAT,pixels);
            require(Float.isFinite(pixels[0]) && pixels[0]>1e-4,"Directional godrays disappeared");
            light[0]=1;light[3]=8;light[8]=-.4472136F;light[10]=-.8944272F;light[11]=1;
            light[12]=.9998F;light[13]=.99939F;
            glBufferData(GL_TEXTURE_BUFFER,light,GL_STATIC_DRAW);
            glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,5,1,GL_RGBA,GL_FLOAT,pixels);
            for(int i=0;i<5;i++)require(pixels[i*4]>1e-5,"Off-axis narrow godray missed by sample jitter");
            for(int i=1;i<5;i++)require(Math.abs(pixels[i*4]-pixels[0])<1e-6,"Non-temporal shafts contain Bayer-pattern grain");
            light[7]=400;glBufferData(GL_TEXTURE_BUFFER,light,GL_STATIC_DRAW);
            glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,5,1,GL_RGBA,GL_FLOAT,pixels);
            require(pixels[0]>.05F && pixels[0]<=.16001F,"Strong godrays wash out the world: "+pixels[0]);
            require(glGetError()==GL_NO_ERROR,"Lighting resolve GL error");
            System.out.println("Texture lighting: lamps remain visible on already-lit surfaces, bounded HDR energy, hue retained, zero-light scene unchanged. Godrays: enabled scatter="+control+", off-axis narrow beams sampled, global off/zero strength/zero density/occlusion all black.");
        }finally {
            glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);glActiveTexture(GL_TEXTURE0);glDeleteProgram(resolve);glDeleteProgram(rays);glDeleteProgram(dark);
            glDeleteFramebuffers(fbo);glDeleteVertexArrays(vao);glDeleteTextures(textures);glDeleteBuffers(buffer);
        }
        verifyPointBias();
        verifySpotFiltering();
        verifyFlashlightEquivalence();
        verifyScatteringBounds();
        verifyScatterResolveEquivalence();
        verifyAoBlurEquivalence();
        verifyAoSamples();
        AoDistanceRegression.verify();
    }
    private static void verifyAoSamples()throws Exception {
        int[] programs={program(expand(Files.readString(Path.of("src/test/resources/ssao/ssao-reference.fsh")))),program(source("shaders/ssao/native/ssao.fsh"))};
        int fbo=glGenFramebuffers();
        int vao=glGenVertexArrays();
        int[] textures=new int[3];
        glGenTextures(textures);
        try {
            int width=96;
            int height=60;
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[0]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,width,height,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[0],0);glDrawBuffer(GL_COLOR_ATTACHMENT0);glReadBuffer(GL_COLOR_ATTACHMENT0);
            glBindVertexArray(vao);glViewport(0,0,width,height);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
            float maximum=0;
            int different=0;
            for(int scene=0;scene<40;scene++) {
                boolean zero=scene%2==0;
                var view=new Matrix4f().rotateX(scene*.13F).rotateY(scene*.21F).rotateZ(scene*.07F);
                var projection=new Matrix4f().perspective((float)Math.toRadians(45+scene),width/(float)height,.05F,200,zero);
                var vp=new Matrix4f(projection).mul(view);
                var inv=new Matrix4f(vp).invert();
                float[] depths=new float[width*height];
                float[] normals=new float[width*height*4];
                for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                    int at=x+y*width;
                    float z=-3F-(float)Math.sin(x*.17F)*.3F-(float)Math.cos(y*.11F)*.2F;
                    if(x>width/2)z-=.65F;
                    var clip=projection.transform(new Vector4f(0,0,z,1));
                    depths[at]=(x>width-5 || y<2)?1:(zero?clip.z/clip.w:clip.z/clip.w*.5F+.5F);
                    var normal=new Vector3f((float)Math.sin(x*.2F)*.2F,(float)Math.sin(y*.13F)*.2F,1).normalize();
                    new Matrix3f(view).transpose().transform(normal);normals[at*4]=normal.x;normals[at*4+1]=normal.y;normals[at*4+2]=normal.z;normals[at*4+3]=1;
                }
                for(int unit=1;unit<=2;unit++) {
                    glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,textures[unit]);
                    glTexImage2D(GL_TEXTURE_2D,0,unit==1?GL_R32F:GL_RGBA32F,width,height,0,unit==1?GL_RED:GL_RGBA,GL_FLOAT,unit==1?depths:normals);
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,scene%4<2?GL_NEAREST:GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,scene%4<2?GL_NEAREST:GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
                }
                float[][] result={new float[width*height*4],new float[width*height*4]};
                for(int i=0;i<2;i++) {
                    int p=programs[i];
                    glUseProgram(p);
                    glUniform1i(glGetUniformLocation(p,"DepthSampler"),1);
                    glUniform1i(glGetUniformLocation(p,"GNormalSampler"),2);
                    glUniform1i(glGetUniformLocation(p,"HasGBuffer"),scene%3==0?0:1);glUniform1i(glGetUniformLocation(p,"ZeroToOne"),zero?1:0);glUniform1i(glGetUniformLocation(p,"Frame"),scene%5==0?0:scene*17);
                    glUniformMatrix4fv(glGetUniformLocation(p,"ViewProj"),false,vp.get(new float[16]));glUniformMatrix4fv(glGetUniformLocation(p,"InvViewProj"),false,inv.get(new float[16]));glUniformMatrix4fv(glGetUniformLocation(p,"View"),false,view.get(new float[16]));
                    glUniform1f(glGetUniformLocation(p,"Radius"),.84F);glUniform1f(glGetUniformLocation(p,"Intensity"),.65F);glUniform1f(glGetUniformLocation(p,"Bias"),.025F);glUniform1f(glGetUniformLocation(p,"Power"),1.18F);
                    glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,width,height,GL_RGBA,GL_FLOAT,result[i]);
                }
                for(int i=0;i<result[0].length;i++){float error=Math.abs(result[0][i]-result[1][i]);maximum=Math.max(maximum,error);if(error!=0)different++;require(Float.isFinite(error) && error==0,"AO samples changed: "+error+", scene="+scene);}
            }
            require(glGetError()==GL_NO_ERROR,"AO sample comparison GL error");
            System.out.println("AO samples: 40 rotated/depth-edge/temporal views, maximum stored-channel error="+maximum+", changed channels="+different);
        }finally {
            glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);glActiveTexture(GL_TEXTURE0);for(int p:programs)glDeleteProgram(p);glDeleteFramebuffers(fbo);glDeleteVertexArrays(vao);glDeleteTextures(textures);
        }
    }
    private static void verifyScatteringBounds()throws Exception {
        int p=program(source("shaders/light/deferred.fsh"));
        int fbo=glGenFramebuffers();
        int vao=glGenVertexArrays();
        int buffer=glGenBuffers();
        int[] textures=new int[3];
        glGenTextures(textures);
        try {
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[0]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,96,60,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[0],0);glDrawBuffer(GL_COLOR_ATTACHMENT0);glReadBuffer(GL_COLOR_ATTACHMENT0);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D,textures[1]);
            float[] depths=new float[96*60];
            for(int i=0;i<depths.length;i++)depths[i]=i%7==0?1:.985F;
            glTexImage2D(GL_TEXTURE_2D,0,GL_R32F,96,60,0,GL_RED,GL_FLOAT,depths);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glActiveTexture(GL_TEXTURE11);glBindTexture(GL_TEXTURE_BUFFER,textures[2]);glBindBuffer(GL_TEXTURE_BUFFER,buffer);glTexBuffer(GL_TEXTURE_BUFFER,GL_RGBA32F,buffer);
            glBindVertexArray(vao);glViewport(0,0,96,60);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glUseProgram(p);
            glUniform1i(glGetUniformLocation(p,"DepthSampler"),1);glUniform1i(glGetUniformLocation(p,"SpotShadowAtlas"),1);
            glUniform1i(glGetUniformLocation(p,"LightTexels"),11);glUniform1i(glGetUniformLocation(p,"MaterialTexels"),12);glUniform1i(glGetUniformLocation(p,"TileLights"),14);
            glUniform1i(glGetUniformLocation(p,"LightCount"),1);glUniform1i(glGetUniformLocation(p,"VolumetricOnly"),1);
            glUniform1i(glGetUniformLocation(p,"VolumetricSteps"),64);glUniform1f(glGetUniformLocation(p,"VolumetricStrength"),.65F);
            glUniform1f(glGetUniformLocation(p,"VolumetricDensity"),.06F);glUniform1f(glGetUniformLocation(p,"VolumetricAniso"),.54F);glUniform1f(glGetUniformLocation(p,"TemporalOffset"),-1);
            float max=0;
            float totalArea=0;
            int nonempty=0;
            var random=new Random(55710);
            for(int scene=0;scene<36;scene++) {
                var projection=new Matrix4f().perspective((float)Math.toRadians(45+scene),1.6F,.05F,200);
                glUniformMatrix4fv(glGetUniformLocation(p,"InvViewProj"),false,new Matrix4f(projection).invert().get(new float[16]));
                var axis=new Vector3f((scene%6-2.5F)*.12F,(scene/6-2.5F)*.1F,-1).normalize();
                float x=.2F;
                float y=-.1F;
                float z=scene%9==0?.1F:-.5F;
                float cos=(float)Math.cos(Math.toRadians(8+scene%15));
                float[] light={x,y,z,36, 1,.8F,.6F,3.2F, axis.x,axis.y,axis.z,1, .998F,cos,0,1, 0,0,0,1, 1,0,0,-1, 0,0,.6F,0, 64,.21F,.15F,0};
                glBindBuffer(GL_TEXTURE_BUFFER,buffer);glBufferData(GL_TEXTURE_BUFFER,light,GL_STREAM_DRAW);
                float[] rect=new float[4];
                SpotlightBounds.project(projection,x,y,z,axis.x,axis.y,axis.z,36,cos,96,60,rect);
                totalArea+=(rect[2]-rect[0])*(rect[3]-rect[1]);
                var u=new Vector3f(0,1,0).cross(axis).normalize();
                var v=new Vector3f(axis).cross(u);
                for(int sample=0;sample<1000;sample++) {
                    float distance=.001F+random.nextFloat()*35.999F;
                    float angle=random.nextFloat()*(float)Math.PI*2;
                    float radius=distance*(float)Math.sqrt(1-cos*cos)/cos*random.nextFloat();
                    var point=new Vector4f(x+axis.x*distance+radius*(u.x*(float)Math.cos(angle)+v.x*(float)Math.sin(angle)),
                            y+axis.y*distance+radius*(u.y*(float)Math.cos(angle)+v.y*(float)Math.sin(angle)),
                            z+axis.z*distance+radius*(u.z*(float)Math.cos(angle)+v.z*(float)Math.sin(angle)),1).mul(projection);
                    if(point.w<=0)continue;float px=point.x/point.w*.5F+.5F,py=point.y/point.w*.5F+.5F;
                    if(px<0 || px>1 || py<0 || py>1)continue;
                    require(px>=rect[0] && px<=rect[2] && py>=rect[1] && py<=rect[3],"Scattering bounds clipped the beam");
                }
                glUniform4f(glGetUniformLocation(p,"ScatteringRect"),rect[0],rect[1],rect[2],rect[3]);
                float[][] pixels={new float[96*60*4],new float[96*60*4]};
                for(int mode=0;mode<2;mode++){glUniform1i(glGetUniformLocation(p,"ScatteringClip"),mode);glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,96,60,GL_RGBA,GL_FLOAT,pixels[mode]);}
                for(int i=0;i<pixels[0].length;i++){float error=Math.abs(pixels[0][i]-pixels[1][i]);max=Math.max(max,error);require(error==0,"Projected cone changed scattering or depth: "+error);if(i%4!=3 && pixels[0][i]>0)nonempty++;}
            }
            require(nonempty>0,"Scattering bounds test only rendered black");require(glGetError()==GL_NO_ERROR,"Scattering bounds GL error");
            System.out.println("Scattering bounds: 36 views, 36,000 cone samples; exact RGB/depth, maximum error="+max+", average viewport="+totalArea/36);
        }finally{glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);glActiveTexture(GL_TEXTURE0);glDeleteProgram(p);glDeleteFramebuffers(fbo);glDeleteVertexArrays(vao);glDeleteTextures(textures);glDeleteBuffers(buffer);}
    }

    private static void verifyAoBlurEquivalence()throws Exception {
        int[] programs={program(expand(Files.readString(Path.of("src/test/resources/ssao/blur-reference.fsh")))),program(source("shaders/ssao/native/blur.fsh"))};
        int fbo=glGenFramebuffers();
        int vao=glGenVertexArrays();
        int[] textures=new int[4];
        glGenTextures(textures);
        try {
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[0]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,32,32,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[0],0);glDrawBuffer(GL_COLOR_ATTACHMENT0);glReadBuffer(GL_COLOR_ATTACHMENT0);
            glBindVertexArray(vao);glViewport(0,0,32,32);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
            float maximum=0;
            int cases=0;
            for(int scene=0;scene<6;scene++)for(int size:new int[]{16,31,32})for(int filter:new int[]{GL_NEAREST,GL_LINEAR}) {
                float[] ao=new float[size*size];
                float[] depth=new float[size*size];
                float[] normals=new float[size*size*4];
                for(int y=0;y<size;y++)for(int x=0;x<size;x++) {
                    int i=x+y*size;
                    ao[i]=switch(scene){case 0->1;case 1->0;case 2->x==size/2 && y==size/2?.2F:1;case 3->x<size/2?0:1;case 4->(x*17+y*11)%13/12F;default->.63F;};
                    depth[i]=x==size-1?1:x<size/2?.3F:.9F;
                    normals[i*4+(y<size/2?2:1)]=1;normals[i*4+3]=1;
                }
                for(int unit=1;unit<=3;unit++) {
                    glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,textures[unit]);
                    glTexImage2D(GL_TEXTURE_2D,0,unit==3?GL_RGBA32F:GL_R32F,size,size,0,unit==3?GL_RGBA:GL_RED,GL_FLOAT,unit==1?ao:unit==2?depth:normals);
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,filter);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,filter);
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
                }
                float[][] result={new float[32*32*4],new float[32*32*4]};
                for(int i=0;i<2;i++) {
                    int p=programs[i];
                    glUseProgram(p);
                    glUniform1i(glGetUniformLocation(p,"AoSampler"),1);
                    glUniform1i(glGetUniformLocation(p,"DepthSampler"),2);
                    glUniform1i(glGetUniformLocation(p,"GNormalSampler"),3);
                    glUniform1i(glGetUniformLocation(p,"HasGBuffer"),scene%2);glUniform1i(glGetUniformLocation(p,"ZeroToOne"),scene%2);
                    glUniformMatrix4fv(glGetUniformLocation(p,"InvViewProj"),false,new Matrix4f().get(new float[16]));
                    glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,32,32,GL_RGBA,GL_FLOAT,result[i]);
                }
                for(int i=0;i<result[0].length;i++){float error=Math.abs(result[0][i]-result[1][i]);maximum=Math.max(maximum,error);require(Float.isFinite(error) && error==0,"AO shortcut changed a depth/normal edge: "+error+", scene="+scene+", size="+size);}
                cases++;
            }
            require(glGetError()==GL_NO_ERROR,"AO blur comparison GL error");
            System.out.println("AO blur: "+cases+" constant/sparse/dense fields, depth/normal edges, nearest/bilinear and odd sizes match exactly; maximum error="+maximum);
        }finally {
            glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);glActiveTexture(GL_TEXTURE0);for(int p:programs)glDeleteProgram(p);glDeleteFramebuffers(fbo);glDeleteVertexArrays(vao);glDeleteTextures(textures);
        }
    }
    private static void verifyScatterResolveEquivalence()throws Exception {
        int[] programs={program(Files.readString(Path.of("src/test/resources/flashlight/upscale-reference.fsh"))),program(source("shaders/volumetric/upscale.fsh"))};
        int fbo=glGenFramebuffers();
        int vao=glGenVertexArrays();
        int[] textures=new int[3];
        glGenTextures(textures);
        try {
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[0]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,32,32,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[0],0);glDrawBuffer(GL_COLOR_ATTACHMENT0);glReadBuffer(GL_COLOR_ATTACHMENT0);
            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D,textures[2]);
            float[] depth=new float[32*32];
            for(int i=0;i<depth.length;i++)depth[i]=i%32<16?.3F:.9F;
            glTexImage2D(GL_TEXTURE_2D,0,GL_R32F,32,32,0,GL_RED,GL_FLOAT,depth);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glBindVertexArray(vao);glViewport(0,0,32,32);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
            float maximum=0;
            for(int scene=0;scene<4;scene++) {
                float[] scatter=new float[8*8*4];
                for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
                    int i=(x+y*8)*4;
                    boolean lit=scene==3 || scene==1 && x==3 && y==3 || scene==2 && x>2 && x<6;
                    if(lit){scatter[i]=.08F;scatter[i+1]=.045F;scatter[i+2]=.02F;}
                    scatter[i+3]=x<4?.3F:.9F;
                }
                glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,textures[1]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,8,8,0,GL_RGBA,GL_FLOAT,scatter);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
                float[][] result={new float[32*32*4],new float[32*32*4]};
                for(int i=0;i<2;i++) {
                    glUseProgram(programs[i]);glUniform1i(glGetUniformLocation(programs[i],"Scatter"),1);glUniform1i(glGetUniformLocation(programs[i],"DepthSampler"),2);
                    glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,32,32,GL_RGBA,GL_FLOAT,result[i]);
                }
                for(int i=0;i<result[0].length;i++){float error=Math.abs(result[0][i]-result[1][i]);maximum=Math.max(maximum,error);require(error<1e-6F,"Empty-shaft optimization changed bilateral edge filtering");}
            }
            require(glGetError()==GL_NO_ERROR,"Scatter resolve comparison GL error");
            System.out.println("Flashlight resolve: empty/sparse/wide/dense scatter and depth edges match reference; maximum error="+maximum);
        }finally {
            glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);glActiveTexture(GL_TEXTURE0);for(int p:programs)glDeleteProgram(p);glDeleteFramebuffers(fbo);glDeleteVertexArrays(vao);glDeleteTextures(textures);
        }
    }
    private static void verifyFlashlightEquivalence()throws Exception {
        realSpotShadows=true;
        String reference=expand(Files.readString(Path.of("src/test/resources/flashlight/deferred-reference.fsh")));
        String optimized=source("shaders/light/deferred.fsh");
        realSpotShadows=false;
        String main="uniform vec3 Endpoint;out vec4 TestResult;void main(){TestResult=vec4(volumetric(Endpoint),1);}";
        int[] programs={program(reference.substring(0,reference.indexOf("void main()"))+main),program(optimized.substring(0,optimized.indexOf("void main()"))+main)};
        int fbo=glGenFramebuffers();
        int vao=glGenVertexArrays();
        int buffer=glGenBuffers();
        int[] textures=new int[3];
        glGenTextures(textures);
        try {
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[0]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,5,1,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[0],0);glDrawBuffer(GL_COLOR_ATTACHMENT0);glReadBuffer(GL_COLOR_ATTACHMENT0);
            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D,textures[2]);
            float[] atlas=new float[32*32];
            for(int y=0;y<32;y++)for(int x=0;x<32;x++)atlas[x+y*32]=x<16?.985F:1F;
            glTexImage2D(GL_TEXTURE_2D,0,GL_R32F,32,32,0,GL_RED,GL_FLOAT,atlas);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_BUFFER,textures[1]);glBindBuffer(GL_TEXTURE_BUFFER,buffer);glBufferData(GL_TEXTURE_BUFFER,32*4L,GL_STATIC_DRAW);glTexBuffer(GL_TEXTURE_BUFFER,GL_RGBA32F,buffer);
            glBindVertexArray(vao);glViewport(0,0,5,1);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
            float[] projection=new Matrix4f().perspective((float)Math.toRadians(40),1,.05F,36).lookAt(.2F,-.1F,-.5F,.2F,-.1F,-1.5F,0,1,0).get(new float[16]);
            for(int p:programs) {
                glUseProgram(p);glUniform1i(glGetUniformLocation(p,"LightTexels"),1);glUniform1i(glGetUniformLocation(p,"LightCount"),1);
                glUniform1i(glGetUniformLocation(p,"SpotShadowAtlas"),2);glUniform1i(glGetUniformLocation(p,"PointShadowArray"),3);glUniform1i(glGetUniformLocation(p,"SunShadowMap"),4);
                glUniform1i(glGetUniformLocation(p,"ShadowGridX"),1);glUniform1f(glGetUniformLocation(p,"ShadowAtlasSize"),32);glUniform1f(glGetUniformLocation(p,"ShadowTileSize"),32);
                glUniform1f(glGetUniformLocation(p,"ShadowBias"),.0004F);glUniformMatrix4fv(glGetUniformLocation(p,"SpotViewProj[0]"),false,projection);
                glUniform1i(glGetUniformLocation(p,"VolumetricSteps"),64);glUniform1f(glGetUniformLocation(p,"VolumetricStrength"),.65F);glUniform1f(glGetUniformLocation(p,"VolumetricDensity"),.06F);
                glUniform1f(glGetUniformLocation(p,"VolumetricAniso"),.54F);glUniform1f(glGetUniformLocation(p,"TemporalOffset"),-1);glUniform1i(glGetUniformLocation(p,"VolumetricShadows"),1);
            }
            float maximum=0;
            for(int test=0;test<40;test++) {
                glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,textures[2]);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,test%4<2?GL_NEAREST:GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,test%4<2?GL_NEAREST:GL_LINEAR);
                float[] light={.2F,-.1F,-.5F,36, 1,1,1,3.2F, 0,0,-1,1, .998F,.974F,0,1, 0,0,0,1, 1,0,0,0, 0,0,.6F,0, 64,.21F,.15F,1};
                glBindBuffer(GL_TEXTURE_BUFFER,buffer);glBufferData(GL_TEXTURE_BUFFER,light,GL_STATIC_DRAW);
                float[][] result={new float[20],new float[20]};
                for(int i=0;i<2;i++) {
                    int p=programs[i];
                    glUseProgram(p);
                    glUniform1i(glGetUniformLocation(p,"ShadowActive"),test%2);
                    glUniform3f(glGetUniformLocation(p,"Endpoint"),(test%10-4.5F)*.15F,0,-2-test*.7F);
                    glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,5,1,GL_RGBA,GL_FLOAT,result[i]);
                }
                for(int i=0;i<20;i++){float error=Math.abs(result[0][i]-result[1][i]);maximum=Math.max(maximum,error);require(error<2e-5F,"Optimized flashlight changed scatter/shadow visibility: "+error);}
            }
            require(glGetError()==GL_NO_ERROR,"Flashlight comparison GL error");
            System.out.println("Flashlight: 40 production/reference spotlight views with real shadow atlas, maximum channel error="+maximum);
        }finally {
            glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);glActiveTexture(GL_TEXTURE0);for(int p:programs)glDeleteProgram(p);glDeleteFramebuffers(fbo);glDeleteVertexArrays(vao);glDeleteTextures(textures);glDeleteBuffers(buffer);
        }
    }
    private static void verifySpotFiltering()throws Exception {
        String shadow=Files.readString(Path.of("src/main/resources/assets/amnetic/shaders/common/shadowmap.glsl"));
        int p=program("#version 410\n"+shadow+"\nuniform float SampleOffset;out vec4 result;void main(){float v=amnetic_spotCompare(vec2(0),vec2(SampleOffset+(gl_FragCoord.x-.5)*.25,2),.5);result=vec4(v,v,v,1);}");
        int fbo=glGenFramebuffers();
        int vao=glGenVertexArrays();
        int target=glGenTextures();
        int atlas=glGenTextures();
        try {
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,target);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,5,1,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,target,0);glDrawBuffer(GL_COLOR_ATTACHMENT0);
            glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,atlas);
            float[] depths={0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1};
            glTexImage2D(GL_TEXTURE_2D,0,GL_R32F,4,4,0,GL_RED,GL_FLOAT,depths);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glUseProgram(p);glUniform1i(glGetUniformLocation(p,"SpotShadowAtlas"),1);glUniform1f(glGetUniformLocation(p,"ShadowTileSize"),4);
            glBindVertexArray(vao);glViewport(0,0,5,1);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);
            for(int edge=0;edge<2;edge++) {
                glUniform1f(glGetUniformLocation(p,"SampleOffset"),edge==0?1.5F:3.5F);
                glDrawArrays(GL_TRIANGLES,0,3);
                float[] pixels=new float[20];
                glReadPixels(0,0,5,1,GL_RGBA,GL_FLOAT,pixels);
                for(int i=0;i<5;i++)require(Math.abs(pixels[i*4]-(edge==0?i*.25F:1F))<1e-5,"Spot shadow transition is stepped or bleeds across its atlas tile: "+pixels[i*4]);
            }
            require(glGetError()==GL_NO_ERROR,"Spot shadow filtering GL error");
            System.out.println("Spot shadows: continuous subtexel visibility across a hard caster edge; atlas borders remain isolated.");
        }finally{glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);glActiveTexture(GL_TEXTURE0);glDeleteTextures(target);glDeleteTextures(atlas);glDeleteFramebuffers(fbo);glDeleteVertexArrays(vao);glDeleteProgram(p);}
    }
    private static void verifyPointBias()throws Exception {
        String shadow=Files.readString(Path.of("src/main/resources/assets/amnetic/shaders/common/shadowmap.glsl"));
        int p=program("#version 410\n"+shadow+"\nuniform int TestShadowRef;out vec4 result;void main(){result=vec4(shadowVisibility(TestShadowRef,vec3(0,0,-2),vec3(0,0,1),vec3(0),10.0),1);}");
        int fbo=glGenFramebuffers();
        int vao=glGenVertexArrays();
        int target=glGenTextures();
        int cube=glGenTextures();
        try {
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,target);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,1,1,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,target,0);glDrawBuffer(GL_COLOR_ATTACHMENT0);
            glActiveTexture(GL_TEXTURE1);
            int type=GL40.GL_TEXTURE_CUBE_MAP_ARRAY;
            glBindTexture(type,cube);
            float depth=(float)(((10.0+.05)/(10.0-.05)-(2*10*.05)/((10-.05)*1.99))*.5+.5);
            float[] faces={depth,depth,depth,depth,depth,depth};
            GL12.glTexImage3D(type,0,GL_R32F,1,1,6,0,GL_RED,GL_FLOAT,faces);
            glTexParameteri(type,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(type,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glUseProgram(p);glUniform1i(glGetUniformLocation(p,"PointShadowArray"),1);
            glUniform1i(glGetUniformLocation(p,"TestShadowRef"),1000);
            glUniform1i(glGetUniformLocation(p,"SunShadowMap"),2);glUniform1i(glGetUniformLocation(p,"ShadowActive"),1);
            glBindVertexArray(vao);glViewport(0,0,1,1);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);
            for(int i=0;i<2;i++) {
                glUniform1f(glGetUniformLocation(p,"ShadowNormalBias"),i==0?0:.05F);
                glDrawArrays(GL_TRIANGLES,0,3);
                float[] pixel=new float[4];
                glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);
                require(Math.abs(pixel[0]-i)<1e-5,"Point shadow ignores surface normal offset: "+pixel[0]+", control="+i);
            }
            require(glGetError()==GL_NO_ERROR,"Point shadow bias GL error");
            System.out.println("Point shadows: real cube-map sampling removes self-shadow acne with normal bias; zero-bias control remains occluded.");
        }finally{glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);glActiveTexture(GL_TEXTURE0);glDeleteTextures(target);glDeleteTextures(cube);glDeleteFramebuffers(fbo);glDeleteVertexArrays(vao);glDeleteProgram(p);}
    }
    private static float linear(float v){return v<=.04045F?v/12.92F:(float)Math.pow((v+.055)/1.055,2.4);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
