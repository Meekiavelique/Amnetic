#version 410 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D AoRaw;
uniform sampler2D LinearSampler;
uniform sampler2D History;
uniform mat4 InvView;
uniform mat4 PrevViewProj;
uniform vec3 EyeDelta;
uniform float Feedback;
uniform sampler2D Velocity;
uniform int HasVelocity;

#include "amnetic:shaders/ssao/view.glsl"

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    ivec2 size = textureSize(AoRaw, 0);
    float cur = texelFetch(AoRaw, px, 0).r;
    float z = abs(texelFetch(LinearSampler, px, 0).r);
    FragColor = vec4(cur, z, 0.0, 1.0);
    if (Feedback <= 0.0 || z > 60000.0) return;

    vec2 uv = (vec2(px) + 0.5) / vec2(size);
    vec3 P = mat3(InvView) * viewAt(uv, z);
    if (HasVelocity == 1) P -= texelFetch(Velocity, ivec2(uv * vec2(textureSize(Velocity, 0))), 0).xyz;
    vec4 pc = PrevViewProj * vec4(P + EyeDelta, 1.0);
    if (pc.w <= 0.0) return;
    vec2 puv = pc.xy / pc.w * 0.5 + 0.5;
    if (any(lessThan(puv, vec2(0.0))) || any(greaterThan(puv, vec2(1.0)))) return;

    vec2 hp = puv * vec2(size) - 0.5;
    ivec2 base = ivec2(floor(hp));
    vec2 f = hp - vec2(base);
    float sum = 0.0;
    float wsum = 0.0;
    for (int i = 0; i < 4; i++) {
        ivec2 o = ivec2(i & 1, i >> 1);
        vec2 h = texelFetch(History, clamp(base + o, ivec2(0), size - 1), 0).rg;
        float bil = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y);
        float w = bil * step(abs(h.g - pc.w), 0.03 * pc.w + 0.02);
        sum += h.r * w;
        wsum += w;
    }
    if (wsum < 0.05) return;
    float hist = sum / wsum;

    float lo = cur;
    float hi = cur;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float n = texelFetch(AoRaw, clamp(px + ivec2(x, y), ivec2(0), size - 1), 0).r;
            lo = min(lo, n);
            hi = max(hi, n);
        }
    }
    hist = clamp(hist, lo - 0.05, hi + 0.05);
    FragColor = vec4(mix(cur, hist, Feedback * min(wsum, 1.0)), z, 0.0, 1.0);
}
