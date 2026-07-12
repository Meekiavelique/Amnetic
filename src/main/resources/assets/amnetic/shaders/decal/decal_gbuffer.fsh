#version 330 core

layout(location = 1) out vec4 OutNormal; // xyz = world normal, a = coverage (drives the blend weight)
layout(location = 2) out vec4 OutMaterial; // r = roughness, g = metallic, b = materialId/255, a = coverage

uniform sampler2D DepthSampler;
uniform sampler2D NormalSampler;
uniform int HasNormalMap;
uniform mat4 InvViewProj;
uniform mat4 DecalInv; // camera-relative world -> decal local (box is [-0.5, 0.5])
uniform int ZeroToOne;
uniform float Opacity;
uniform float AngleFade;
uniform vec3 DecalAxis; // decal facing = surface normal it sticks to
uniform vec3 DecalRight; // tangent basis (local X)
uniform vec3 DecalUp; // tangent basis (local Z)
uniform float Roughness;
uniform float Metallic;

#include "amnetic:shaders/common/screen.glsl"

vec3 normalFromDepth(vec2 uv, vec3 P) {
    vec2 ts = 1.0 / vec2(textureSize(DepthSampler, 0));
    vec3 px = reconstruct(uv + vec2(ts.x, 0.0), texture(DepthSampler, uv + vec2(ts.x, 0.0)).r) - P;
    vec3 py = reconstruct(uv + vec2(0.0, ts.y), texture(DepthSampler, uv + vec2(0.0, ts.y)).r) - P;
    vec3 n = cross(px, py);
    float l = length(n);
    n = (l > 1e-8) ? n / l : vec3(0.0, 1.0, 0.0);
    if (dot(n, P) > 0.0) n = -n;
    return n;
}

void main() {
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(DepthSampler, 0));
    float depth = texture(DepthSampler, uv).r;
    if (depth >= 1.0) discard;

    vec3 P = reconstruct(uv, depth);
    vec3 local = (DecalInv * vec4(P, 1.0)).xyz;
    if (abs(local.x) > 0.5 || abs(local.y) > 0.5 || abs(local.z) > 0.5) discard;
    vec2 duv = local.xz + 0.5;

    vec3 axis = normalize(DecalAxis);
    vec3 Nsurf = normalFromDepth(uv, P);
    float fade = smoothstep(AngleFade, 1.0, dot(Nsurf, axis));
    fade *= 1.0 - smoothstep(0.35, 0.5, abs(local.y));
    if (fade <= 0.0) discard;
    float cov = Opacity * fade;

    vec3 wN = axis;
    if (HasNormalMap == 1) {
        vec3 tn = texture(NormalSampler, duv).xyz * 2.0 - 1.0;
        tn.xy *= 3.0;
        wN = normalize(tn.x * normalize(DecalRight) + tn.y * normalize(DecalUp) + tn.z * axis);
    }

    OutNormal = vec4(wN, cov);
    OutMaterial = vec4(clamp(Roughness, 0.0, 1.0), clamp(Metallic, 0.0, 1.0), 0.0, cov); // materialId 0 = default PBR
}
