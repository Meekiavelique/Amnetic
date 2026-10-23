#version 410 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D AoSampler;
uniform sampler2D LinearSampler;
uniform sampler2D NormalSampler;
uniform ivec2 Direction;

#include "amnetic:shaders/ssao/view.glsl"

const float KERNEL[5] = float[5](1.0, 4.0, 6.0, 4.0, 1.0);

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    ivec2 size = textureSize(AoSampler, 0);
    vec2 texel = 1.0 / vec2(size);
    float z = abs(texelFetch(LinearSampler, px, 0).r);
    float center = texelFetch(AoSampler, px, 0).r;
    if (z > 60000.0) { FragColor = vec4(1.0); return; }
    vec3 P = viewAt((vec2(px) + 0.5) * texel, z);
    vec3 N = normalize(texelFetch(NormalSampler, px, 0).xyz * 2.0 - 1.0);
    float tolerance = 1.0 / (0.01 * z + 0.02);

    float sum = 0.0;
    float wsum = 0.0;
    for (int k = -2; k <= 2; k++) {
        ivec2 q = clamp(px + Direction * k, ivec2(0), size - 1);
        float zq = abs(texelFetch(LinearSampler, q, 0).r);
        vec3 Pq = viewAt((vec2(q) + 0.5) * texel, zq);
        vec3 Nq = normalize(texelFetch(NormalSampler, q, 0).xyz * 2.0 - 1.0);
        float plane = abs(dot(N, Pq - P)) * tolerance;
        float facing = max(dot(N, Nq), 0.0);
        facing *= facing;
        facing *= facing;
        float w = KERNEL[k + 2] * max(1.0 - plane, 0.0) * facing;
        sum += texelFetch(AoSampler, q, 0).r * w;
        wsum += w;
    }
    FragColor = vec4(wsum > 1e-3 ? sum / wsum : center);
}
