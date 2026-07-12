#version 430 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D ColorSampler;
uniform sampler2D DepthSampler;
uniform sampler2D GNormalSampler;
uniform int HasGBuffer;
uniform mat4 ViewProj;
uniform mat4 InvViewProj;
uniform int ZeroToOne;
uniform float Radius;
uniform float Intensity;

#include "amnetic:shaders/common/screen.glsl"

const vec3 KERNEL[16] = vec3[16](
    vec3(0.05, 0.03, 0.06), vec3(-0.09, 0.07, 0.12),
    vec3(0.14, -0.11, 0.08), vec3(-0.16, -0.12, 0.19),
    vec3(0.22, 0.18, 0.12), vec3(-0.07, 0.28, 0.24),
    vec3(0.31, -0.06, 0.30), vec3(-0.28, -0.30, 0.18),
    vec3(0.09, 0.38, 0.42), vec3(-0.44, 0.16, 0.36),
    vec3(0.50, -0.36, 0.22), vec3(-0.18, -0.52, 0.46),
    vec3(0.60, 0.28, 0.52), vec3(-0.36, 0.60, 0.40),
    vec3(0.66, -0.52, 0.66), vec3(-0.72, -0.36, 0.60)
);

void main() {
    float depth = texture(DepthSampler, vUV).r;
    if (depth >= 1.0) { FragColor = vec4(0.0); return; }

    vec3 P = reconstruct(vUV, depth);
    vec4 gn = (HasGBuffer == 1) ? texture(GNormalSampler, vUV) : vec4(0.0);
    vec3 N = (gn.a > 0.5) ? normalize(gn.xyz) : betterNormal(vUV, P);

    ivec2 px = ivec2(gl_FragCoord.xy);
    float a = float((px.x & 3) * 4 + (px.y & 3)) / 16.0 * 6.2831853;
    // robust basis that won't NaN when N aligns with the dither plane, the source of black splotches
    vec3 up = abs(N.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
    vec3 T0 = normalize(cross(up, N));
    vec3 B0 = cross(N, T0);
    float c = cos(a), s = sin(a);
    vec3 T = T0 * c + B0 * s;
    vec3 B = -T0 * s + B0 * c;
    mat3 TBN = mat3(T, B, N);

    vec3 gi = vec3(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 16; i++) {
        vec3 sp = P + (TBN * KERNEL[i]) * Radius;
        vec4 clip = ViewProj * vec4(sp, 1.0);
        if (clip.w <= 0.0) continue;
        vec2 suv = clip.xy / clip.w * 0.5 + 0.5;
        if (suv.x < 0.0 || suv.x > 1.0 || suv.y < 0.0 || suv.y > 1.0) continue;
        float sd = texture(DepthSampler, suv).r;
        if (sd >= 1.0) continue;

        vec3 sP = reconstruct(suv, sd);
        vec3 dir = sP - P;
        float dist = length(dir);
        if (dist < 1e-3) continue;
        if (dist > Radius) continue; // reject far hits so distant geometry can't bleed colour in
        dir /= dist;

        float ndl = max(dot(N, dir), 0.0);
        if (ndl <= 0.0) continue;
        float facing = 1.0;
        if (HasGBuffer == 1) {
            vec4 sn = texture(GNormalSampler, suv);
            if (sn.a > 0.5) facing = max(dot(normalize(sn.xyz), -dir), 0.0);
        }
        // smooth distance falloff + weight-normalised accumulation, dividing by sample count washed out
        float atten = 1.0 - dist / Radius;
        float w = ndl * facing * atten * atten;
        gi += texture(ColorSampler, suv).rgb * w;
        wsum += w;
    }
    gi = (wsum > 1e-4) ? gi / wsum : vec3(0.0);
    // one bounce: tint by the receiving surface's albedo so it reads as reflected light, not additive haze
    gi *= texture(ColorSampler, vUV).rgb * Intensity;
    FragColor = vec4(clamp(gi, 0.0, 4.0), 1.0);
}
