#version 410 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D CurrentSampler;
uniform sampler2D DepthSampler;
uniform sampler2D HistorySampler;

uniform mat4 InvViewProj;
uniform mat4 PrevViewProj;
uniform vec3 EyeDelta;
uniform int ZeroToOne;
uniform int HasHistory;
uniform float HistoryBlend;

#include "amnetic:shaders/common/screen.glsl"

void main() {
    vec3 current = texture(CurrentSampler, vUV).rgb;
    float depth = texture(DepthSampler, vUV).r;
    if (IS_SKY(depth) || HasHistory == 0) {
        FragColor = vec4(current, 0.0);
        return;
    }

    vec3 P = reconstruct(vUV, depth);
    vec3 prevRelative = P + EyeDelta;
    vec4 prevClip = PrevViewProj * vec4(prevRelative, 1.0);
    if (prevClip.w <= 0.0) { FragColor = vec4(current, 0.0); return; }
    vec2 prevUV = prevClip.xy / prevClip.w * 0.5 + 0.5;
    if (prevUV.x < 0.0 || prevUV.x > 1.0 || prevUV.y < 0.0 || prevUV.y > 1.0) {
        FragColor = vec4(current, 0.0);
        return;
    }

    vec4 hist = texture(HistorySampler, prevUV);
    float prevDist = hist.a;
    float curDist = length(prevRelative);
    bool disocclusion = prevDist <= 0.0 || abs(curDist - prevDist) > max(curDist, 1.0) * 0.05;

    if (disocclusion) {
        FragColor = vec4(current, curDist);
        return;
    }

    vec3 blended = mix(current, hist.rgb, HistoryBlend);
    FragColor = vec4(blended, curDist);
}
