#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform vec3 Forward;
uniform vec3 Right;
uniform vec3 Up;

uniform samplerCube Source;
uniform float SourceSize;
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

void main() {
    vec2 c = vUV * 2.0 - 1.0;
    vec3 N = normalize(Forward + Right * c.x + Up * c.y);
    vec3 up = abs(N.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
    vec3 tx = normalize(cross(up, N));
    vec3 ty = cross(N, tx);
    float texelAngle = 4.0 * PI / (6.0 * SourceSize * SourceSize);
    vec3 sum = vec3(0.0);
    uint count = uint(Samples);
    for (uint i = 0u; i < count; i++) {
        vec2 xi = vec2(float(i) / float(count), radicalInverse(i));
        float phi = 2.0 * PI * xi.x;
        float cosT = sqrt(1.0 - xi.y);
        float sinT = sqrt(xi.y);
        vec3 L = normalize(tx * cos(phi) * sinT + ty * sin(phi) * sinT + N * cosT);
        float pdf = max(cosT, 1e-3) / PI;
        float lod = max(0.5 * log2(1.0 / (float(count) * pdf) / texelAngle) + 1.0, 0.0);
        sum += textureLod(Source, L, lod).rgb;
    }
    FragColor = vec4(sum / float(count), 1.0);
}
