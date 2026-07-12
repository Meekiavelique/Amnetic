#version 430 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D AoSampler;
uniform sampler2D DepthSampler;
uniform mat4 InvViewProj;
uniform int ZeroToOne;

#include "amnetic:shaders/common/screen.glsl"

// 5x5 bilateral blur over the AO to average out the 4x4 dither pattern. taps are weighted by
// relative linear distance instead of absolute units so the denoise still works on distant
// objects without smearing across silhouettes
void main() {
    float cd = texture(DepthSampler, vUV).r;
    if (cd >= 1.0) { FragColor = vec4(1.0); return; }

    vec3 cP = reconstruct(vUV, cd);
    float centerDist = length(cP);
    vec2 texel = 1.0 / vec2(textureSize(AoSampler, 0));

    float sum = 0.0;
    float wsum = 0.0;
    for (int x = -2; x <= 2; x++) {
        for (int y = -2; y <= 2; y++) {
            vec2 uv = vUV + vec2(float(x), float(y)) * texel;
            float d = texture(DepthSampler, uv).r;
            if (d >= 1.0) continue;

            vec3 p = reconstruct(uv, d);
            float dist = length(p);

            // edge-stop: reject taps with more than about 5% relative depth difference
            float w = exp(-abs(dist - centerDist) / (centerDist * 0.05 + 1e-4));

            sum += texture(AoSampler, uv).r * w;
            wsum += w;
        }
    }
    FragColor = vec4(vec3(wsum > 0.0 ? sum / wsum : 1.0), 1.0);
}
