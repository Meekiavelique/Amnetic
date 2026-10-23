#version 410 core

in vec2 vUV;
layout(location = 5) out vec4 GVelocity;

uniform sampler2D DepthSampler;
uniform mat4 InvViewProj;
uniform int ZeroToOne;
uniform int BoxCount;
uniform vec4 Boxes[96];

#include "amnetic:shaders/common/screen.glsl"

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    if (IS_SKY(depth)) discard;
    vec3 P = reconstruct(vUV, depth);
    for (int i = 0; i < BoxCount; i++) {
        vec3 lo = Boxes[i * 3].xyz;
        vec3 hi = Boxes[i * 3 + 1].xyz;
        if (all(greaterThanEqual(P, lo)) && all(lessThanEqual(P, hi))) {
            GVelocity = vec4(Boxes[i * 3 + 2].xyz, 1.0);
            return;
        }
    }
    discard;
}
