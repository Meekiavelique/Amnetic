#version 430 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D Raw; // current reflection (rgb = colour, a = strength)
uniform sampler2D History; // previous resolved reflection
uniform sampler2D DepthSampler;
uniform mat4 PrevViewProj;
uniform mat4 InvViewProj;
uniform vec3 EyeDelta;
uniform int ZeroToOne;
uniform float Feedback;

#include "amnetic:shaders/common/screen.glsl"

// temporal accumulation: reproject this pixel into last frame and blend, which removes the
// per-pixel ray-march noise. a neighbourhood colour clamp prevents ghosting when the reflection
// changes on disocclusion or motion
void main() {
    vec4 cur = texture(Raw, vUV);
    float depth = texture(DepthSampler, vUV).r;
    if (depth >= 1.0) { FragColor = cur; return; }

    vec3 P = reconstruct(vUV, depth);
    vec4 pc = PrevViewProj * vec4(P + EyeDelta, 1.0);
    vec4 outc = cur;
    if (pc.w > 0.0) {
        vec2 puv = pc.xy / pc.w * 0.5 + 0.5;
        if (puv.x >= 0.0 && puv.x <= 1.0 && puv.y >= 0.0 && puv.y <= 1.0) {
            vec4 hist = texture(History, puv);
            vec2 ts = 1.0 / vec2(textureSize(Raw, 0));
            vec4 lo = cur, hi = cur, n;
            n = texture(Raw, vUV + vec2(ts.x, 0.0)); lo = min(lo, n); hi = max(hi, n);
            n = texture(Raw, vUV - vec2(ts.x, 0.0)); lo = min(lo, n); hi = max(hi, n);
            n = texture(Raw, vUV + vec2(0.0, ts.y)); lo = min(lo, n); hi = max(hi, n);
            n = texture(Raw, vUV - vec2(0.0, ts.y)); lo = min(lo, n); hi = max(hi, n);
            hist = clamp(hist, lo, hi);
            outc = mix(cur, hist, Feedback);
        }
    }
    FragColor = outc;
}
