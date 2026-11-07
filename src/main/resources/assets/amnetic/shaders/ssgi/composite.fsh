#version 410 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D GiSampler;
uniform sampler2D DepthSampler;
uniform mat4 InvViewProj;
uniform int ZeroToOne;

#include "amnetic:shaders/common/screen.glsl"

void main() {
    float centerD = texture(DepthSampler, vUV).r;
    if (IS_SKY(centerD)) { FragColor = vec4(0.0); return; }

    vec3 cP = reconstruct(vUV, centerD);
    float centerDist = length(cP);
    vec2 ts = 1.0 / vec2(textureSize(GiSampler, 0));

    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
            vec2 o = vec2(float(x), float(y)) * ts;
            float d = texture(DepthSampler, vUV + o).r;
            if (IS_SKY(d)) continue;
            vec3 p = reconstruct(vUV + o, d);
            float w = exp(-abs(length(p) - centerDist) / (centerDist * 0.05 + 1e-4));
            sum += texture(GiSampler, vUV + o).rgb * w;
            wsum += w;
        }
    }
    FragColor = vec4(sum / max(wsum, 1e-4), 1.0);
}
