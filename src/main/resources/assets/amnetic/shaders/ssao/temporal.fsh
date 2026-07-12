#version 430 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D AoRaw;
uniform sampler2D History;
uniform sampler2D DepthSampler;
uniform mat4 PrevViewProj;
uniform mat4 InvViewProj;
uniform vec3 EyeDelta;
uniform int ZeroToOne;
uniform float Feedback;

#include "amnetic:shaders/common/screen.glsl"

void main() {
    float cur = texture(AoRaw, vUV).r;
    float depth = texture(DepthSampler, vUV).r;
    if (depth >= 1.0) { FragColor = vec4(vec3(cur), 1.0); return; }

    vec3 P = reconstruct(vUV, depth);
    vec4 pc = PrevViewProj * vec4(P + EyeDelta, 1.0);
    float outAo = cur;
    if (pc.w > 0.0) {
        vec2 puv = pc.xy / pc.w * 0.5 + 0.5;
        if (puv.x >= 0.0 && puv.x <= 1.0 && puv.y >= 0.0 && puv.y <= 1.0) {
            float hist = texture(History, puv).r;
            vec2 ts = 1.0 / vec2(textureSize(AoRaw, 0));
            float lo = cur, hi = cur;
            float n;
            n = texture(AoRaw, vUV + vec2(ts.x, 0)).r; lo = min(lo, n); hi = max(hi, n);
            n = texture(AoRaw, vUV - vec2(ts.x, 0)).r; lo = min(lo, n); hi = max(hi, n);
            n = texture(AoRaw, vUV + vec2(0, ts.y)).r; lo = min(lo, n); hi = max(hi, n);
            n = texture(AoRaw, vUV - vec2(0, ts.y)).r; lo = min(lo, n); hi = max(hi, n);
            hist = clamp(hist, lo, hi);
            outAo = mix(cur, hist, Feedback);
        }
    }
    FragColor = vec4(vec3(outAo), 1.0);
}
