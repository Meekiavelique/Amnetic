#version 430 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D DepthSampler;
uniform sampler2D GNormalSampler;
uniform int HasGBuffer;
uniform mat4 ViewProj;
uniform mat4 InvViewProj;
uniform mat4 View;
uniform int ZeroToOne;
uniform float Radius;
uniform float Intensity;
uniform float Bias;
uniform float Power;
uniform int Frame;

#include "amnetic:shaders/common/screen.glsl"

float viewZ(vec3 camRelWorld) {
    return -(View * vec4(camRelWorld, 1.0)).z;
}

const vec3 KERNEL[16] = vec3[16](
    vec3(0.121, 0.112, 0.330), vec3(-0.245, 0.338, 0.461),
    vec3(0.472, -0.255, 0.540), vec3(-0.583, -0.261, 0.695),
    vec3(0.610, 0.692, 0.161), vec3(-0.234, 0.740, 0.320),
    vec3(0.855, -0.230, 0.350), vec3(-0.840, -0.350, 0.290),
    vec3(0.345, 0.890, 0.410), vec3(-0.820, 0.480, 0.380),
    vec3(0.950, -0.480, 0.310), vec3(-0.490, -0.860, 0.430),
    vec3(0.800, 0.640, 0.560), vec3(-0.680, 0.800, 0.500),
    vec3(0.630, -0.860, 0.630), vec3(-0.960, -0.580, 0.700)
);

void main() {
    float depth = texture(DepthSampler, vUV).r;
    if (depth >= 1.0) { FragColor = vec4(1.0); return; }

    vec3 P = reconstruct(vUV, depth);
    vec4 gn = (HasGBuffer == 1) ? texture(GNormalSampler, vUV) : vec4(0.0);
    vec3 N = (gn.a > 0.5) ? normalize(gn.xyz) : betterNormal(vUV, P);

    // robust TBN that won't NaN out if N aligns with a hardcoded world vector
    vec3 up = abs(N.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(up, N));
    vec3 bitangent = cross(N, tangent);
    mat3 TBN = mat3(tangent, bitangent, N);

    // 4x4 dither rotated in tangent space. the ordered offset decorrelates neighbouring pixels,
    // the per-frame golden-ratio advance rotates the kernel every frame so the temporal pass
    // integrates fresh samples instead of freezing on one estimate. Frame is held at 0 when
    // temporal accumulation is off so the pattern stays stable, no flicker
    ivec2 px = ivec2(gl_FragCoord.xy);
    float base = float((px.x & 3) * 4 + (px.y & 3)) / 16.0;
    float a = fract(base + float(Frame) * 0.6180339887) * 6.2831853;
    float c = cos(a), s = sin(a);
    mat3 rot = mat3(
        c, s, 0.0,
       -s, c, 0.0,
       0.0, 0.0, 1.0
    );
    TBN = TBN * rot;

    float pz = viewZ(P);
    float occlusion = 0.0;
    for (int i = 0; i < 16; i++) {
        // cluster samples toward the origin (quadratic) so most taps land in the near field where
        // contact occlusion lives. normalising first removes the >1.0 magnitudes in the hardcoded
        // kernel that used to sample past Radius and inflate variance
        float scale = mix(0.1, 1.0, float(i * i) / 256.0);
        vec3 sp = P + (TBN * (normalize(KERNEL[i]) * scale)) * Radius;
        vec4 clip = ViewProj * vec4(sp, 1.0);
        if (clip.w <= 0.0) continue;
        vec2 suv = (clip.xy / clip.w) * 0.5 + 0.5;
        if (suv.x < 0.0 || suv.x > 1.0 || suv.y < 0.0 || suv.y > 1.0) continue;

        float sd = texture(DepthSampler, suv).r;
        if (sd >= 1.0) continue;
        vec3 sceneP = reconstruct(suv, sd);

        float sampleZ = viewZ(sp);
        float sceneZ = viewZ(sceneP);

        // linear falloff that hits exactly 0 at Radius, kills the background halo
        float rangeCheck = smoothstep(0.0, 1.0, max(1.0 - abs(pz - sceneZ) / Radius, 0.0));
        if (sceneZ < sampleZ - Bias) occlusion += rangeCheck;
    }

    float ao = 1.0 - (occlusion / 16.0) * Intensity;
    ao = pow(clamp(ao, 0.0, 1.0), Power);
    FragColor = vec4(vec3(ao), 1.0);
}
