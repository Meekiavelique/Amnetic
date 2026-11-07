#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform vec3 Forward;
uniform vec3 Right;
uniform vec3 Up;

uniform samplerCube Source;
uniform float SourceSize;
uniform float Roughness;
uniform int Samples;

const float PI = 3.14159265359;

float radicalInverse(uint bits) {
    bits = (bits << 16u) | (bits >> 16u);
    bits = ((bits & 0x55555555u) << 1u) | ((bits & 0xAAAAAAAAu) >> 1u);
    bits = ((bits & 0x33333333u) << 2u) | ((bits & 0xCCCCCCCCu) >> 2u);
    bits = ((bits & 0x0F0F0F0Fu) << 4u) | ((bits & 0xF0F0F0F0u) >> 4u);
    bits = ((bits & 0x00FF00FFu) << 8u) | ((bits & 0xFF00FF00u) >> 8u);
    return float(bits) * 2.3283064365386963e-10;
}

vec3 aroundNormal(vec3 h, vec3 n) {
    vec3 up = abs(n.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
    vec3 tx = normalize(cross(up, n));
    vec3 ty = cross(n, tx);
    return normalize(tx * h.x + ty * h.y + n * h.z);
}

float distributionGGX(float NoH, float a) {
    float a2 = a * a;
    float d = NoH * NoH * (a2 - 1.0) + 1.0;
    return a2 / max(PI * d * d, 1e-7);
}

void main() {
    vec2 c = vUV * 2.0 - 1.0;
    vec3 N = normalize(Forward + Right * c.x + Up * c.y);
    if (Samples <= 1) {
        FragColor = vec4(textureLod(Source, N, 0.0).rgb, 1.0);
        return;
    }
    float a = Roughness * Roughness;
    float texelAngle = 4.0 * PI / (6.0 * SourceSize * SourceSize);
    vec3 sum = vec3(0.0);
    float weight = 0.0;
    uint count = uint(Samples);
    for (uint i = 0u; i < count; i++) {
        vec2 xi = vec2(float(i) / float(count), radicalInverse(i));
        float phi = 2.0 * PI * xi.x;
        float cosT = sqrt((1.0 - xi.y) / (1.0 + (a * a - 1.0) * xi.y));
        float sinT = sqrt(1.0 - cosT * cosT);
        vec3 H = aroundNormal(vec3(cos(phi) * sinT, sin(phi) * sinT, cosT), N);
        vec3 L = normalize(2.0 * dot(N, H) * H - N);
        float NoL = dot(N, L);
        if (NoL <= 0.0) continue;
        float pdf = distributionGGX(max(dot(N, H), 0.0), a) * 0.25;
        float sampleAngle = 1.0 / (float(count) * pdf + 1e-4);
        float lod = max(0.5 * log2(sampleAngle / texelAngle) + 1.0, 0.0);
        sum += textureLod(Source, L, lod).rgb * NoL;
        weight += NoL;
    }
    FragColor = vec4(sum / max(weight, 1e-4), 1.0);
}
