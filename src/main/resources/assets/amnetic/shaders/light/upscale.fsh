#version 410 core

in vec2 vUV;
out vec4 FragColor;
uniform sampler2D LightSampler;
uniform sampler2D DepthSampler;
uniform sampler2D LowDepthSampler;
uniform mat4 InvViewProj;
uniform int ZeroToOne;
#include "amnetic:shaders/common/screen.glsl"

void main() {
    float depth = texture(DepthSampler, vUV).r;
    if (depth >= 1.0) { FragColor = vec4(0.0); return; }
    float sceneDistance = length(reconstruct(vUV, depth));
    ivec2 size = textureSize(LightSampler, 0);
    vec2 position = vUV * vec2(size) - 0.5;
    ivec2 base = ivec2(floor(position));
    vec2 fraction = fract(position);
    vec3 sum = vec3(0.0);
    float weights = 0.0;
    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
        ivec2 pixel = clamp(base + ivec2(x, y), ivec2(0), size - 1);
        vec2 uv = (vec2(pixel) + 0.5) / vec2(size);
        float lowDepth = texelFetch(LowDepthSampler, pixel, 0).r;
        if (lowDepth >= 1.0) continue;
        float difference = abs(length(reconstruct(uv, lowDepth)) - sceneDistance);
        float tolerance = max(0.1, sceneDistance * 0.005);
        float bilinear = (x == 0 ? 1.0 - fraction.x : fraction.x)
                       * (y == 0 ? 1.0 - fraction.y : fraction.y);
        float weight = bilinear * exp(-difference / tolerance);
        sum += texelFetch(LightSampler, pixel, 0).rgb * weight;
        weights += weight;
    }
    FragColor = vec4(weights > 1e-5 ? max(sum / weights, vec3(0.0)) : vec3(0.0), 0.0);
}
