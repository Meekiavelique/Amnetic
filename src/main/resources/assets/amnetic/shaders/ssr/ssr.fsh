#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D ColorSampler;
uniform sampler2D DepthSampler;
uniform sampler2D GNormalSampler;
uniform sampler2D GMaterialSampler;
uniform int HasGBuffer;

uniform mat4 ViewProj;
uniform mat4 InvViewProj;
uniform mat4 View; // world (camera-relative) to view, rotation only
uniform int ZeroToOne;

uniform float Intensity;
uniform int MaxSteps;
uniform float Stride;
uniform float MaxDistance;
uniform float Thickness;
uniform float EdgeFade;
uniform float Reflectivity;
uniform float Frame; // per-frame seed so the ray jitter varies and temporal accumulation can denoise it

#include "amnetic:shaders/common/screen.glsl"

float linZ(vec3 pCamRel) {
    return -(View * vec4(pCamRel, 1.0)).z;
}

float sceneZ(vec2 uv) {
    return linZ(reconstruct(uv, texture(DepthSampler, uv).r));
}

// analytic sky used as the off-screen reflection fallback. R is camera-relative world space, .y is up
vec3 skyEnv(vec3 d) {
    float up = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 zenith = vec3(0.30, 0.50, 0.92);
    vec3 horizon = vec3(0.78, 0.86, 1.00);
    vec3 ground = vec3(0.34, 0.31, 0.27);
    vec3 sky = mix(horizon, zenith, pow(up, 0.55));
    return d.y >= 0.0 ? sky : mix(horizon, ground, clamp(-d.y * 2.5, 0.0, 1.0));
}

void main() {
    float depth = texture(DepthSampler, vUV).r;
    if (depth >= 1.0) { FragColor = vec4(0.0); return; }

    vec3 P = reconstruct(vUV, depth);

    vec3 N;
    float baseRefl;
    float roughFactor;
    vec4 gn = (HasGBuffer == 1) ? texture(GNormalSampler, vUV) : vec4(0.0);
    if (gn.a > 0.5) {
        N = normalize(gn.xyz);
        vec4 gm = texture(GMaterialSampler, vUV);
        float rough = gm.r;
        float f0 = gm.b;
        baseRefl = f0;
        roughFactor = 1.0 - rough;
    } else {
        // no per-pixel roughness here, vanilla terrain/entities don't write the g-buffer material
        // target. treating that as a perfect mirror gave every silhouette a full-strength sky-tinted
        // fresnel rim at grazing angles, so assume a conservative matte roughness instead
        N = betterNormal(vUV, P);
        baseRefl = Reflectivity;
        roughFactor = 0.2;
    }

    if (roughFactor < 0.01) { FragColor = vec4(0.0); return; }

    vec3 V = normalize(P);
    vec3 R = reflect(V, N);

    float jitter = fract(sin(dot(gl_FragCoord.xy + Frame * 5.1234, vec2(12.9898, 78.233))) * 43758.5453);

    // geometric distance-adaptive stepping: short steps near the origin where precision matters,
    // growing toward MaxDistance so few iterations still reach far. the binary search below
    // recovers the exact crossing, so coarse steps cost no accuracy
    float growth = 1.0 + 3.0 / float(MaxSteps);
    float curStep = (MaxDistance / float(MaxSteps)) * 0.5 * (0.5 + 0.5 * jitter);

    vec3 cur = P + N * 0.05;
    vec3 prev = cur;
    float traveled = 0.0;

    vec2 hitUV = vec2(-1.0);
    float hit = 0.0;

    for (int i = 0; i < MaxSteps; i++) {
        prev = cur;
        cur += R * curStep;
        traveled += curStep;
        curStep *= growth;
        if (traveled > MaxDistance) break;

        vec4 clip = ViewProj * vec4(cur, 1.0);
        if (clip.w <= 0.0) break;
        vec2 suv = (clip.xy / clip.w) * 0.5 + 0.5;
        if (any(lessThan(suv, vec2(0.0))) || any(greaterThan(suv, vec2(1.0)))) break;

        float sd = texture(DepthSampler, suv).r;
        if (sd >= 1.0) continue; // marched over sky, keep going

        float dRay = linZ(cur);
        float dScene = sceneZ(suv);
        if (dRay > dScene) { // ray crossed behind a surface in linear depth
            vec3 a = prev; // last point in front
            vec3 b = cur; // first point behind
            for (int r = 0; r < 8; r++) { // binary refine the crossing
                vec3 mid = (a + b) * 0.5;
                vec4 mc = ViewProj * vec4(mid, 1.0);
                vec2 muv = (mc.xy / mc.w) * 0.5 + 0.5;
                if (linZ(mid) > sceneZ(muv)) b = mid; else a = mid;
            }
            vec4 hc = ViewProj * vec4(b, 1.0);
            hitUV = (hc.xy / hc.w) * 0.5 + 0.5;
            float gap = linZ(b) - sceneZ(hitUV);
            if (gap < Thickness) hit = 1.0; // reject overshoot into empty space behind thin geo
            break;
        }
    }

    float fres = baseRefl + (1.0 - baseRefl) * pow(1.0 - max(dot(-V, N), 0.0), 5.0);
    vec3 sky = skyEnv(R);

    // blend the screen-space hit toward the sky fallback near the screen edge / max distance so
    // reflections of off-screen geometry don't hard-cut (the "I see the rest when I jump" artifact).
    // on a clean miss just reflect the sky. cheap stand-in for a reflection probe
    vec3 hitColor;
    if (hit > 0.5) {
        vec3 ssrColor = texture(ColorSampler, hitUV).rgb;
        vec2 e = smoothstep(vec2(0.0), vec2(EdgeFade), hitUV)
               * (1.0 - smoothstep(vec2(1.0) - vec2(EdgeFade), vec2(1.0), hitUV));
        float edge = e.x * e.y;
        float distFade = 1.0 - clamp(length(reconstruct(hitUV, texture(DepthSampler, hitUV).r) - P) / MaxDistance, 0.0, 1.0);
        hitColor = mix(sky, ssrColor, edge * distFade);
    } else {
        hitColor = sky;
    }

    float strength = clamp(fres * roughFactor * Intensity, 0.0, 1.0);
    FragColor = vec4(hitColor, strength);
}
