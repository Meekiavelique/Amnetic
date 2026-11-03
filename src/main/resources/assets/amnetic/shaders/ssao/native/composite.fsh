#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D AoSampler;
uniform sampler2D DepthSampler;
uniform sampler2D DistanceSampler;
uniform mat4 InvViewProj;
uniform int ZeroToOne;
uniform int EdgeAware;

#include "amnetic:shaders/common/screen.glsl"

void main() {
    if(EdgeAware==1) {
        float depth=texture(DepthSampler,vUV).r;
        if(depth<=0.0){FragColor=vec4(1.0);return;}
        float distance=length(reconstruct(vUV,depth));
        ivec2 size=textureSize(AoSampler,0);
        vec2 pixel=vUV*vec2(size)-.5;
        ivec2 base=ivec2(floor(pixel));vec2 fraction=fract(pixel);
        float sum=0.0,weight=0.0;
        for(int y=0;y<2;y++)for(int x=0;x<2;x++) {
            ivec2 tap=clamp(base+ivec2(x,y),ivec2(0),size-1);
            float neighbour=texelFetch(DistanceSampler,tap,0).r;
            if(neighbour<0.0)continue;
            float w=(x==0?1.0-fraction.x:fraction.x)*(y==0?1.0-fraction.y:fraction.y)
                *exp(-abs(neighbour-distance)/max(.02,distance*.01));
            sum+=texelFetch(AoSampler,tap,0).r*w;weight+=w;
        }
        FragColor=vec4(vec3(weight>1e-6?sum/weight:1.0),1.0);return;
    }
    FragColor = vec4(vec3(texture(AoSampler, vUV).r), 1.0);
}
