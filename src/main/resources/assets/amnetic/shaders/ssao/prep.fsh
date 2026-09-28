#version 410 core

in vec2 vUV;
layout(location = 0) out vec4 LinearOut;
layout(location = 1) out vec4 NormalOut;

uniform sampler2D DepthSampler;

#include "amnetic:shaders/ssao/view.glsl"

vec3 at(ivec2 t, vec2 full) {
    return viewAt((vec2(t) + 0.5) / full, linearDepth(texelFetch(DepthSampler, t, 0).r));
}

vec3 side(vec3 P, vec3 lo, vec3 hi) {
    return abs(lo.z - P.z) < abs(hi.z - P.z) ? P - lo : hi - P;
}

void main() {
    ivec2 size = textureSize(DepthSampler, 0);
    vec2 full = vec2(size);
    ivec2 src = min(ivec2(vUV * full), size - 1);
    float d = texelFetch(DepthSampler, src, 0).r;
    if (d >= 1.0) {
        LinearOut = vec4(65504.0);
        NormalOut = vec4(0.5, 0.5, 1.0, 0.0);
        return;
    }
    vec3 P = viewAt((vec2(src) + 0.5) / full, linearDepth(d));
    vec3 dx = side(P, at(max(src - ivec2(1, 0), ivec2(0)), full), at(min(src + ivec2(1, 0), size - 1), full));
    vec3 dy = side(P, at(max(src - ivec2(0, 1), ivec2(0)), full), at(min(src + ivec2(0, 1), size - 1), full));
    vec3 n = normalize(cross(dx, dy));
    if (dot(n, P) > 0.0) n = -n;
    LinearOut = vec4(-P.z);
    NormalOut = vec4(n * 0.5 + 0.5, 1.0);
}
