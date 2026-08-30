#version 430 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D Raw; // current half-res scatter: rgb = scatter, a = scene depth
uniform sampler2D History; // previous resolved scatter
uniform sampler2D DepthSampler; // full-res scene depth
uniform mat4 PrevViewProj;
uniform mat4 InvViewProj;
uniform vec3 EyeDelta;
uniform int ZeroToOne;
uniform float Feedback;

#include "amnetic:shaders/common/screen.glsl"

void main() {
    vec4 cur = texture(Raw, vUV);
    float depth = texture(DepthSampler, vUV).r;
    vec3 outc = cur.rgb;

    if (Feedback > 0.0) {
        // sky pixels have no surface to reproject. treat them as a direction and reproject by camera
        // rotation alone (PrevViewProj is rotation + projection, so dropping EyeDelta drops the
        // translation). the neighbourhood clamp below bounds the error from ignoring parallax
        vec3 P = (depth < 1.0) ? reconstruct(vUV, depth) + EyeDelta : reconstruct(vUV, 0.5);
        vec4 pc = PrevViewProj * vec4(P, 1.0);
        if (pc.w > 0.0) {
            vec2 puv = pc.xy / pc.w * 0.5 + 0.5;
            if (puv.x >= 0.0 && puv.x <= 1.0 && puv.y >= 0.0 && puv.y <= 1.0) {
                vec3 hist = texture(History, puv).rgb;
                vec2 ts = 1.0 / vec2(textureSize(Raw, 0));
                vec3 lo = cur.rgb, hi = cur.rgb, n;
                n = texture(Raw, vUV + vec2(ts.x, 0)).rgb; lo = min(lo, n); hi = max(hi, n);
                n = texture(Raw, vUV - vec2(ts.x, 0)).rgb; lo = min(lo, n); hi = max(hi, n);
                n = texture(Raw, vUV + vec2(0, ts.y)).rgb; lo = min(lo, n); hi = max(hi, n);
                n = texture(Raw, vUV - vec2(0, ts.y)).rgb; lo = min(lo, n); hi = max(hi, n);
                hist = clamp(hist, lo, hi);
                outc = mix(cur.rgb, hist, Feedback);
            }
        }
    }
    FragColor = vec4(outc, cur.a); // keep depth in alpha for the upscale
}
