#version 430 core

in vec2 vUV;
out vec4 FragColor;

#include "amnetic:shaders/light/lighting.glsl"

// forward scattering glow toward the light source
float hgPhase(float cosT, float g) {
    float g2 = g * g;
    float denom = 1.0 + g2 - 2.0 * g * cosT;
    return (1.0 - g2) / (4.0 * 3.14159265 * pow(max(denom, 1e-4), 1.5));
}

// smoother than hash noise, plays nicer with temporal accumulation
const float BAYER4[16] = float[16](
0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
float bayerDither() {
    int x = int(mod(gl_FragCoord.x, 4.0));
    int y = int(mod(gl_FragCoord.y, 4.0));
    return (BAYER4[y * 4 + x] + 0.5) / 16.0;
}

// far bound for rays that hit no geometry. per-light sphere clipping decides the real march
// interval, so this only has to be past every light's reach
const float SKY_RAY_DISTANCE = 8192.0;

// volumetric light shafts, only marches inside each light's sphere so steps aren't wasted
// light needs shadow casting on for the beam to be blocked by geometry
vec3 volumetric(vec3 fragPos) {
    float segLen = length(fragPos);
    if (segLen < 1e-3) return vec3(0.0);
    vec3 rayDir = fragPos / segLen;
    float dither = fract(bayerDither() + TemporalOffset);

    vec3 result = vec3(0.0);
    for (int i = 0; i < LightCount; i++) {
        int type = int(lights[i * 8 + 2].w + 0.5);
        if (type == 2) continue; // directional has no position to sphere-clip against
        float gstr = lights[i * 8 + 6].z; // per-light strength (0 = no shaft)
        if (gstr <= 0.0) continue;

        // per-light volumetric params (lane 7)
        int baseSteps = int(lights[i * 8 + 7].x + 0.5);
        float density = lights[i * 8 + 7].y;
        float aniso = lights[i * 8 + 7].z;
        bool lShadows = lights[i * 8 + 7].w > 0.5;
        if (baseSteps <= 0) continue;

        vec3 pos = lights[i * 8 + 0].xyz;
        float range = lights[i * 8 + 0].w;

        // clip the ray to the light's sphere so we only march where it matters
        vec3 oc = -pos;
        float b = dot(oc, rayDir);
        float c = dot(oc, oc) - range * range;
        float disc = b * b - c;
        if (disc < 0.0) continue;
        float sq = sqrt(disc);
        float tN = max(-b - sq, 0.0);
        float tF = min(-b + sq, segLen);
        if (tF <= tN) continue;

        // per-light step LOD: a light far from the camera (relative to its size) gets fewer steps
        float camDist = length(pos);
        int steps = int(max(4.0, float(baseSteps) * clamp((range * 2.0) / (range * 2.0 + camDist), 0.25, 1.0)));
        float stepLen = (tF - tN) / float(steps);
        int curve = int(lights[i * 8 + 3].z + 0.5);
        float param = lights[i * 8 + 3].w;
        int sref = int(lights[i * 8 + 5].w);
        vec3 lcol = lights[i * 8 + 1].rgb * lights[i * 8 + 1].w;
        vec3 spotDir = normalize(lights[i * 8 + 2].xyz);
        float cosIn = lights[i * 8 + 3].x, cosOut = lights[i * 8 + 3].y;

        // front-to-back with beer-lambert extinction so the integral saturates instead of blowing
        // up into a giant blob when the camera sits inside the light's sphere
        vec3 acc = vec3(0.0);
        float transmittance = 1.0;
        for (int s = 0; s < steps; s++) {
            vec3 p = rayDir * (tN + stepLen * (float(s) + dither));
            vec3 toL = pos - p;
            float d = length(toL);
            if (d > range) continue;
            vec3 L = toL / max(d, 1e-4);
            float a = falloff(d, range, curve, param);
            if (type == 1) {
                float cd = dot(-L, spotDir);
                a *= clamp((cd - cosOut) / max(cosIn - cosOut, 1e-4), 0.0, 1.0);
            }
            if (a <= 0.0) continue;
            if (lShadows) a *= shadowVisibilityCheap(sref, p, pos, range); // single tap, cheap
            float phase = hgPhase(dot(rayDir, L), aniso);
            float sigma = density * a; // local extinction/scatter coefficient
            float stepT = exp(-sigma * stepLen);
            // energy-conserving single scatter: integral of inscatter * transmittance over the step
            acc += transmittance * lcol * phase * (1.0 - stepT);
            transmittance *= stepT;
            if (transmittance < 0.003) break;
        }
        result += acc * gstr;
    }
    return max(result * 6.0, vec3(0.0)); // small global gain so per-light strengths land in a usable range
}

#include "amnetic:shaders/material/custom_ladder.glsl"

void main() {
    float depth = texture(DepthSampler, vUV).r;

    // late volumetric-only pass over translucents, outputs god-rays additively
    // alpha carries depth for bilateral upscale
    if (VolumetricOnly == 1) {
        vec3 fp;
        if (depth >= 1.0) {
            vec3 rd = reconstruct(vUV, 0.5);
            float rl = length(rd);
            if (rl < 1e-5) { FragColor = vec4(0.0, 0.0, 0.0, depth); return; }
            fp = rd * (SKY_RAY_DISTANCE / rl);
        } else {
            fp = reconstruct(vUV, depth);
        }
        FragColor = vec4(volumetric(fp), depth);
        return;
    }

    if (depth >= 1.0) { FragColor = vec4(texture(AlbedoSampler, vUV).rgb, 1.0); return; } // sky: pass scene through, composite replaces rather than adds

    vec3 fragPos = reconstruct(vUV, depth);
    vec3 N;
    float rough = 0.7;
    float f0 = 0.04;
    float metallic = 0.0;
    int materialId = 0;
    vec2 lightUV = vec2(0.0);
    if (HasGBuffer == 1) {
        vec4 gn = texture(GNormalSampler, vUV);
        N = (gn.a > 0.5) ? normalize(gn.xyz) : betterNormal(vUV, fragPos);
        vec4 gm = texture(GMaterialSampler, vUV);
        if (gn.a > 0.5) {
            rough = gm.x;
            metallic = gm.y;
            f0 = mix(0.04, 1.0, metallic);
            materialId = int(gm.z * 255.0 + 0.5);
            float packedLight = gm.w * 255.0;
            float blockIdx = floor(packedLight / 16.0 + 0.5);
            float skyIdx = packedLight - blockIdx * 16.0;
            lightUV = vec2((blockIdx + 0.5) / 16.0, (clamp(skyIdx, 0.0, 15.0) + 0.5) / 16.0);
        }
    } else {
        N = betterNormal(vUV, fragPos);
    }
    // specular AA: widen roughness by the pixel-footprint normal variance so distant and
    // curved surfaces stop sparkling instead of aliasing the highlight
    vec3 nDx = dFdx(N), nDy = dFdy(N);
    float nVar = 0.25 * (dot(nDx, nDx) + dot(nDy, nDy));
    rough = clamp(sqrt(rough * rough + min(2.0 * nVar, 0.18)), 0.0, 1.0);

    vec3 V = normalize(-fragPos); // camera at origin in view space
    vec3 albedo = texture(AlbedoSampler, vUV).rgb;

    if (DebugMode != 0) {
        vec3 dbg = vec3(0.0);
        if (DebugMode == 1) dbg = fract(fragPos * 0.0625);
        else if (DebugMode == 2) dbg = N * 0.5 + 0.5;
        else if (DebugMode == 3) dbg = vec3(0.0, 0.12, 0.0);
        FragColor = vec4(dbg, 1.0);
        return;
    }

    vec3 radiance = vec3(0.0);
    vec3 specular = vec3(0.0);
    float sunShadow = 0.0;
    for (int i = 0; i < LightCount; i++) {
        // when light volumes are on they shade every non-directional light, so the fullscreen
        // pass only still needs the directional sun (which darkens and cannot be additive)
        if (SkipLocalLights == 1 && int(lights[i * 8 + 2].w + 0.5) != 2) continue;
        radiance += lightContribution(i, fragPos, N, V, rough, f0, specular, sunShadow);
    }

    // scene is already day-lit, darken where the sun is occluded, then local lights and specular on top
    float sunMul = clamp(1.0 - sunShadow, 0.0, 1.0);
    vec3 outColor = albedo * sunMul + albedo * radiance + specular; // god-rays added in a separate pass
    outColor = max(outColor, vec3(0.0));
    if (any(isnan(outColor)) || any(isinf(outColor))) outColor = vec3(0.0);

    if (materialId != 0) {
        bool handled;
        vec3 custom = shadeCustomMaterial(materialId,
                GBufferSample(albedo, N, fragPos, rough, metallic, outColor,
                        texture(LightmapSampler, lightUV).rgb), handled);
        if (handled) outColor = custom;
    }

    // triangular dither to kill banding in dark areas
    float r0 = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    float r1 = fract(sin(dot(gl_FragCoord.xy, vec2(93.9898, 67.345))) * 24634.6345);
    outColor += vec3((r0 + r1 - 1.0) / 255.0);

    FragColor = vec4(outColor, 1.0);
}

