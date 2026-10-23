#version 410 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D AoSampler;
uniform sampler2D LinearSampler;
uniform sampler2D DepthSampler;
uniform sampler2D ShadeSampler;

#include "amnetic:shaders/ssao/view.glsl"

float upsampled() {
    float d = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    if (IS_SKY(d)) discard;
    float z = linearDepth(d);
    ivec2 size = textureSize(AoSampler, 0);
    vec2 hp = vUV * vec2(size) - 0.5;
    ivec2 base = ivec2(floor(hp));
    vec2 f = hp - vec2(base);
    float sum = 0.0;
    float wsum = 0.0;
    float best = 1.0;
    float bestGap = 1e30;
    for (int i = 0; i < 4; i++) {
        ivec2 o = ivec2(i & 1, i >> 1);
        ivec2 q = clamp(base + o, ivec2(0), size - 1);
        float zq = abs(texelFetch(LinearSampler, q, 0).r);
        float ao = texelFetch(AoSampler, q, 0).r;
        float gap = abs(zq - z);
        float bil = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y);
        float w = bil / (1.0 + gap * 50.0 / z);
        sum += ao * w;
        wsum += w;
        if (gap < bestGap) { bestGap = gap; best = ao; }
    }
    return bestGap > 0.05 * z ? best : sum / max(wsum, 1e-5);
}

void main() {
    vec4 shade = texelFetch(ShadeSampler, ivec2(gl_FragCoord.xy), 0);
    float share = shade.b > 0.5 ? shade.r : 1.0;
    if (share < 0.004) discard;
    FragColor = vec4(vec3(1.0 - share * (1.0 - upsampled())), 1.0);
}
