#version 330 core

#define BATCH 32

out vec4 FragColor;

flat in int Index;

uniform sampler2D DepthSampler;
uniform sampler2D DecalSampler;
uniform int HasTexture;
uniform mat4 InvViewProj;
uniform int ZeroToOne;
uniform int Multiply;
uniform mat4 DecalInvs[BATCH]; // camera-relative world -> decal local (box is [-0.5, 0.5])
uniform vec4 Axes[BATCH]; // xyz = surface normal the decal sticks to
uniform vec4 Params[BATCH]; // x = opacity, y = angle fade, z = flip v
uniform vec4 Tints[BATCH];

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
    if (IS_SKY(depth)) discard;

    vec3 P = reconstruct(uv, depth);
    vec3 local = (DecalInvs[Index] * vec4(P, 1.0)).xyz;
    if (abs(local.x) > 0.5 || abs(local.y) > 0.5 || abs(local.z) > 0.5) discard;

    vec4 params = Params[Index];
    vec2 duv = local.xz + 0.5;
    if (params.z > 0.5) duv.y = 1.0 - duv.y;

    float fade = params.y > 0.0 ? smoothstep(params.y, 1.0, dot(normalFromDepth(uv, P), Axes[Index].xyz)) : 1.0;
    fade *= 1.0 - smoothstep(0.35, 0.5, abs(local.y));
    if (fade <= 0.0) discard;

    vec3 col;
    float a;
    if (HasTexture == 1) {
        vec4 t = texture(DecalSampler, duv);
        col = t.rgb * Tints[Index].rgb;
        a = t.a;
    } else {
        float r = length(duv - 0.5) * 2.0;
        a = 1.0 - smoothstep(0.55, 1.0, r);
        col = Tints[Index].rgb;
    }

    a *= params.x * fade;
    if (a <= 0.001) discard;
    if (Multiply == 1) {
        FragColor = vec4(mix(vec3(1.0), col, a), 1.0);
        return;
    }
    FragColor = vec4(col, a);
}
