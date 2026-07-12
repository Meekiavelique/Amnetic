#version 430 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D CurrentSampler; // this frame's raw single-sample GI
uniform sampler2D DepthSampler; // this frame's scene depth, gi-buffer resolution
uniform sampler2D HistorySampler; // previous accumulated GI: rgb = color, a = camera-relative distance

uniform mat4 InvViewProj; // current frame, gi-buffer projection
uniform mat4 PrevViewProj; // previous frame's view-projection, same camera-relative convention
uniform vec3 EyeDelta; // currentEye - prevEye, world units
uniform int ZeroToOne;
uniform int HasHistory; // 0 on the first frame or right after a resize/reset
uniform float HistoryBlend; // EMA weight given to history (0 = none, close to 1 = very sticky)

#include "amnetic:shaders/common/screen.glsl"

// reprojects last frame's accumulated GI into the current pixel using depth + the camera delta,
// rejects on disocclusion (off-screen or large depth mismatch), and blends surviving history with
// this frame's fresh sample via an EMA. the bilateral blur in ssgi/composite.fsh runs after this
// and mops up the remaining per-frame noise
void main() {
    vec3 current = texture(CurrentSampler, vUV).rgb;
    float depth = texture(DepthSampler, vUV).r;
    if (depth >= 1.0 || HasHistory == 0) {
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
    // disocclusion: previous surface at that reprojected point was at a meaningfully different distance
    bool disocclusion = prevDist <= 0.0 || abs(curDist - prevDist) > max(curDist, 1.0) * 0.05;

    if (disocclusion) {
        FragColor = vec4(current, curDist);
        return;
    }

    vec3 blended = mix(current, hist.rgb, HistoryBlend);
    FragColor = vec4(blended, curDist);
}
