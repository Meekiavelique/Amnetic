#version 430 core

in vec2 vUV;

layout(location = 1) out vec4 GNormal; // xyz normal (camera-relative), a = coverage
layout(location = 2) out vec4 GMaterial; // r = roughness, g = metallic, b = materialId/255, a = unused

uniform sampler2D DepthSampler;
uniform mat4 InvViewProj;
uniform int ZeroToOne;
uniform float DefaultRoughness;

#include "amnetic:shaders/common/screen.glsl"

void main() {
    float c = texture(DepthSampler, vUV).r;
    if (c >= 1.0) { // sky: no surface, leave coverage 0
        GNormal = vec4(0.0);
        GMaterial = vec4(0.0);
        return;
    }

    vec2 ts = 1.0 / vec2(textureSize(DepthSampler, 0));
    vec3 P = reconstruct(vUV, c);

    float dl = texture(DepthSampler, vUV - vec2(ts.x, 0.0)).r;
    float dr = texture(DepthSampler, vUV + vec2(ts.x, 0.0)).r;
    float dd = texture(DepthSampler, vUV - vec2(0.0, ts.y)).r;
    float du = texture(DepthSampler, vUV + vec2(0.0, ts.y)).r;

    vec3 Px = (abs(dl - c) < abs(dr - c)) ? (P - reconstruct(vUV - vec2(ts.x, 0.0), dl))
                                          : (reconstruct(vUV + vec2(ts.x, 0.0), dr) - P);
    vec3 Py = (abs(dd - c) < abs(du - c)) ? (P - reconstruct(vUV - vec2(0.0, ts.y), dd))
                                          : (reconstruct(vUV + vec2(0.0, ts.y), du) - P);

    vec3 n = cross(Px, Py);
    float l = length(n);
    n = (l > 1e-7) ? n / l : normalize(-P);
    if (dot(n, P) > 0.0) n = -n; // camera-relative origin is the eye, normal faces it

    GNormal = vec4(n, 1.0); // coverage 1: a real normal is now present here
    GMaterial = vec4(clamp(DefaultRoughness, 0.0, 1.0), 0.0, 0.0, 0.0); // materialId 0 = default PBR
}
