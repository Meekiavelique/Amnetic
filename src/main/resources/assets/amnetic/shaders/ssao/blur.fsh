#version 430 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D AoSampler;
uniform sampler2D DepthSampler;
uniform sampler2D GNormalSampler;
uniform int HasGBuffer;
uniform mat4 InvViewProj;
uniform int ZeroToOne;

#include "amnetic:shaders/common/screen.glsl"

void main() {
    float cd = texture(DepthSampler, vUV).r;
    if (cd >= 1.0) { FragColor = vec4(1.0); return; }

    vec3 cP = reconstruct(vUV, cd);
    float centerDist = length(cP);
    vec3 cN = (HasGBuffer == 1) ? texture(GNormalSampler, vUV).xyz : vec3(0.0);
    bool haveN = HasGBuffer == 1 && length(cN) > 0.5;
    cN = haveN ? normalize(cN) : cN;

    vec2 texel = 1.0 / vec2(textureSize(AoSampler, 0));

    float sum = 0.0;
    float wsum = 0.0;
    for (int x = -3; x <= 3; x++) {
        for (int y = -3; y <= 3; y++) {
            vec2 uv = vUV + vec2(float(x), float(y)) * texel;
            float d = texture(DepthSampler, uv).r;
            if (d >= 1.0) continue;

            vec3 p = reconstruct(uv, d);
            float dist = length(p);

            float wd = exp(-abs(dist - centerDist) / (centerDist * 0.1 + 1e-4));

            float wn = 1.0;
            if (haveN) {
                vec3 n = texture(GNormalSampler, uv).xyz;
                if (length(n) > 0.5) {
                    float nd = max(dot(cN, normalize(n)), 0.0);
                    wn = nd * nd * nd * nd;
                }
            }

            float w = wd * wn;
            sum += texture(AoSampler, uv).r * w;
            wsum += w;
        }
    }
    FragColor = vec4(vec3(wsum > 0.0 ? sum / wsum : texture(AoSampler, vUV).r), 1.0);
}
