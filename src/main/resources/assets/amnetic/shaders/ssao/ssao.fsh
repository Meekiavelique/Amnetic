#version 410 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D LinearSampler;
uniform sampler2D NormalSampler;
uniform float Radius;
uniform float Intensity;
uniform float Bias;
uniform float Power;
uniform float PixelScale;
uniform int Frame;

#include "amnetic:shaders/ssao/view.glsl"

const float PI = 3.14159265;
const float HALF_PI = 1.57079633;
const int SLICES = 2;
const int STEPS = 4;

float fastAcos(float x) {
    float a = abs(x);
    float r = (-0.156583 * a + HALF_PI) * sqrt(1.0 - a);
    return x >= 0.0 ? r : PI - r;
}

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    ivec2 size = textureSize(LinearSampler, 0);
    float signedZ = texelFetch(LinearSampler, px, 0).r;
    float z = abs(signedZ);
    bool terrain = signedZ < 0.0;
    vec4 packedN = texelFetch(NormalSampler, px, 0);
    float reach = Radius * PixelScale / z;
    if (z > 60000.0 || packedN.a < 0.5 || reach < 1.0) { FragColor = vec4(1.0); return; }

    vec2 texel = 1.0 / vec2(size);
    vec2 uv = (vec2(px) + 0.5) * texel;
    vec3 N = normalize(packedN.xyz * 2.0 - 1.0);
    vec3 P = viewAt(uv, z) + N * Bias;
    vec3 V = normalize(-P);
    reach = min(reach, 96.0);

    float falloffRange = 0.615 * Radius;
    float falloffMul = -1.0 / falloffRange;
    float falloffAdd = (Radius - falloffRange) / falloffRange + 1.0;

    vec2 seed = vec2(px) + 5.588238 * float(Frame & 63);
    float sliceNoise = interleaved(seed);
    float stepNoise = interleaved(seed + vec2(47.0, 17.0));

    float visibility = 0.0;
    for (int s = 0; s < SLICES; s++) {
        float phi = (float(s) + sliceNoise) * PI / float(SLICES);
        vec2 omega = vec2(cos(phi), sin(phi));
        vec3 dir = vec3(omega, 0.0);
        vec3 ortho = dir - dot(dir, V) * V;
        vec3 axis = normalize(cross(ortho, V));
        vec3 projN = N - axis * dot(N, axis);
        float projLen = length(projN);
        float cosN = clamp(dot(projN, V) / max(projLen, 1e-4), 0.0, 1.0);
        float n = (dot(ortho, projN) >= 0.0 ? 1.0 : -1.0) * fastAcos(cosN);
        float low0 = cos(n + HALF_PI);
        float low1 = cos(n - HALF_PI);
        float h0 = low0;
        float h1 = low1;
        for (int j = 0; j < STEPS; j++) {
            float t = (float(j) + stepNoise) / float(STEPS);
            vec2 offset = omega * (1.0 + t * t * (reach - 1.0));
            vec2 a = floor(vec2(px) + 0.5 + offset) + 0.5;
            vec2 b = floor(vec2(px) + 0.5 - offset) + 0.5;
            if (all(greaterThanEqual(a, vec2(0.0))) && all(lessThan(a, vec2(size)))) {
                float za = texelFetch(LinearSampler, ivec2(a), 0).r;
                if (terrain && za < 0.0) za = 65504.0;
                vec3 d = viewAt(a * texel, za) - P;
                float l = length(d);
                float w = clamp(l * falloffMul + falloffAdd, 0.0, 1.0);
                h0 = max(h0, mix(low0, dot(d, V) / l, w));
            }
            if (all(greaterThanEqual(b, vec2(0.0))) && all(lessThan(b, vec2(size)))) {
                float zb = texelFetch(LinearSampler, ivec2(b), 0).r;
                if (terrain && zb < 0.0) zb = 65504.0;
                vec3 d = viewAt(b * texel, zb) - P;
                float l = length(d);
                float w = clamp(l * falloffMul + falloffAdd, 0.0, 1.0);
                h1 = max(h1, mix(low1, dot(d, V) / l, w));
            }
        }
        float a0 = n + clamp(-fastAcos(clamp(h1, -1.0, 1.0)) - n, -HALF_PI, HALF_PI);
        float a1 = n + clamp(fastAcos(clamp(h0, -1.0, 1.0)) - n, -HALF_PI, HALF_PI);
        float sinN = sin(n);
        float arc0 = (cosN + 2.0 * a0 * sinN - cos(2.0 * a0 - n)) * 0.25;
        float arc1 = (cosN + 2.0 * a1 * sinN - cos(2.0 * a1 - n)) * 0.25;
        visibility += projLen * (arc0 + arc1);
    }
    visibility = clamp(visibility / float(SLICES), 0.0, 1.0);
    float ao = pow(clamp(1.0 - (1.0 - visibility) * Intensity, 0.0, 1.0), Power);
    FragColor = vec4(ao);
}
