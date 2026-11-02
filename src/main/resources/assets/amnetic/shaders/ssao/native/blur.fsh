#version 410 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D AoSampler;
uniform sampler2D DepthSampler;
uniform sampler2D GNormalSampler;
uniform int HasGBuffer;
uniform mat4 InvViewProj;
uniform int ZeroToOne;
uniform sampler2D DistanceSampler;
uniform int PreparedDistance;

#include "amnetic:shaders/common/screen.glsl"

void main() {
    float cd = texture(DepthSampler, vUV).r;
    if (cd >= 1.0) { FragColor = vec4(1.0); return; }

    vec2 texel = 1.0 / vec2(textureSize(AoSampler, 0));
    float centerAo = texture(AoSampler, vUV).r;
    if (centerAo == 1.0 || centerAo == 0.0) {
        bool constantAo = true;
        for (int x = -3; x <= 3 && constantAo; x += 2) {
            for (int y = -3; y <= 3; y += 2) {
                if (any(notEqual(textureGather(AoSampler, vUV + vec2(x, y) * texel, 0), vec4(centerAo)))) {
                    constantAo = false;
                    break;
                }
            }
        }
        if (constantAo) { FragColor = vec4(vec3(centerAo), 1.0); return; }
    }

    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 size = textureSize(AoSampler, 0);
    float centerDist = PreparedDistance == 1 ? texelFetch(DistanceSampler, pixel, 0).r
                                            : length(reconstruct(vUV, cd));
    vec3 cN = (HasGBuffer == 1) ? texture(GNormalSampler, vUV).xyz : vec3(0.0);
    bool haveN = HasGBuffer == 1 && length(cN) > 0.5;
    cN = haveN ? normalize(cN) : cN;

    float sum = 0.0;
    float wsum = 0.0;
    for (int x = -3; x <= 3; x++) {
        for (int y = -3; y <= 3; y++) {
            vec2 uv = vUV + vec2(float(x), float(y)) * texel;
            ivec2 neighbour = pixel + ivec2(x, y);
            float dist;
            if (PreparedDistance == 1 && all(greaterThanEqual(neighbour, ivec2(0))) && all(lessThan(neighbour, size))) {
                dist = texelFetch(DistanceSampler, neighbour, 0).r;
                if (dist < 0.0) continue;
            } else {
                float d = texture(DepthSampler, uv).r;
                if (d >= 1.0) continue;
                dist = length(reconstruct(uv, d));
            }

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
