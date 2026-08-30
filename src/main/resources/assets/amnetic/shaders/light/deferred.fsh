#version 430 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D AlbedoSampler;
uniform sampler2D DepthSampler;
uniform sampler2D GNormalSampler;
uniform sampler2D GMaterialSampler; // r = roughness, g = metallic, b = materialId/255, a = (blockLevel*16 + skyLevel)/255
uniform sampler2D LightmapSampler;
uniform int HasGBuffer;

uniform mat4 InvViewProj;
uniform int ZeroToOne;
uniform int LightCount;
uniform int VolumetricSteps;
uniform float VolumetricStrength;
uniform float VolumetricDensity;
uniform float VolumetricAniso; // henyey-greenstein g value, 0 to 0.95
uniform int DebugMode;

uniform mat4 ViewProj;
uniform int ContactSteps; // 0 = off
uniform float ContactDistance;
uniform float ContactThickness; // how thick an occluder needs to be to count
uniform int VolumetricShadows;
uniform int VolumetricOnly; // late pass, runs over translucents
uniform float TemporalOffset; // jitter seed changes each frame
uniform sampler2D CookieSampler;
uniform int HasCookie;

layout(std430, binding = 0) readonly buffer LightData { vec4 lights[]; };
layout(std430, binding = 1) readonly buffer MaterialParamData { vec4 materialParams[]; };

#include "amnetic:shaders/common/screen.glsl"
#include "amnetic:shaders/common/shadowmap.glsl"

// fake ies profiles by id, shapes the angular falloff
float iesProfile(int id, float c) {
    c = clamp(c, 0.0, 1.0);
    if (id == 1) return smoothstep(0.0, 1.0, c);
    if (id == 2) return 0.4 + 0.6 * c;
    if (id == 3) return abs(cos(acos(c) * 3.0));
    if (id == 4) return pow(c, 4.0);
    if (id == 5) return 0.5 + 0.5 * sin(acos(c) * 5.0);
    return 1.0;
}

// screen-space contact shadows, catches small occluders shadow maps miss
float contactShadow(vec3 P, vec3 L) {
    if (ContactSteps <= 0) return 1.0;
    vec3 stepv = L * (ContactDistance / float(ContactSteps));
    vec3 p = P + stepv * 1.5; // skip self
    for (int i = 0; i < ContactSteps; i++) {
        vec4 clip = ViewProj * vec4(p, 1.0);
        if (clip.w <= 0.0) return 1.0;
        vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return 1.0;
        float sd = texture(DepthSampler, uv).r;
        if (sd < 1.0) {
            vec3 sceneP = reconstruct(uv, sd);
            float diff = length(p) - length(sceneP);
            if (diff > 0.02 && diff < ContactThickness) return 0.0;
        }
        p += stepv;
    }
    return 1.0;
}

// projects the cookie texture through the spot cone to tint the light
vec3 cookieTint(vec3 fragPos, vec3 pos, vec3 dir, float cosOut, vec3 tangent) {
    vec3 fdir = normalize(dir);
    vec3 d = normalize(fragPos - pos);
    float cosToFrag = dot(d, fdir);
    if (cosToFrag <= 1e-3) return vec3(1.0);
    vec3 right = normalize(cross(fdir, normalize(tangent)));
    vec3 up = cross(right, fdir);
    float spread = max(tan(acos(clamp(cosOut, -0.999, 0.999))), 1e-3);
    vec2 uv = vec2(dot(d, right), dot(d, up)) / (cosToFrag * spread) * 0.5 + 0.5;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return vec3(1.0);
    return texture(CookieSampler, uv).rgb;
}

// distance attenuation curves
float falloff(float dist, float range, int curve, float param) {
    float t = clamp(dist / max(range, 1e-4), 0.0, 1.0);
    if (curve == 0) return 1.0 - smoothstep(0.0, 1.0, t);
    if (curve == 1) return 1.0 - t;
    if (curve == 2) { // inverse square, windowed so it hits zero at range
        float d2 = dist * dist;
        float win = clamp(1.0 - (d2 * d2) / (range * range * range * range), 0.0, 1.0);
        return (win * win) / (d2 + 1.0);
    }
    return pow(1.0 - t, param);
}

// nearest point on a line segment, used for tube lights
vec3 closestSegment(vec3 p, vec3 a, vec3 b) {
    vec3 ab = b - a;
    float t = clamp(dot(p - a, ab) / max(dot(ab, ab), 1e-4), 0.0, 1.0);
    return a + ab * t;
}

#include "amnetic:shaders/light/styles.glsl"

// diffuse + specular for one light, specular goes into specOut so it skips albedo multiply
vec3 lightContribution(int i, vec3 fragPos, vec3 N, vec3 V, float rough, float f0, inout vec3 specOut, inout float sunShadowOut) {
    vec3 pos = lights[i * 8 + 0].xyz;
    float range = lights[i * 8 + 0].w;
    vec3 color = lights[i * 8 + 1].rgb;
    float intens = lights[i * 8 + 1].w;
    vec3 dir = lights[i * 8 + 2].xyz;
    int type = int(lights[i * 8 + 2].w + 0.5);
    float cosIn = lights[i * 8 + 3].x;
    float cosOut = lights[i * 8 + 3].y;
    int curve = int(lights[i * 8 + 3].z + 0.5);
    float param = lights[i * 8 + 3].w;
    float areaW = lights[i * 8 + 4].x;
    float areaH = lights[i * 8 + 4].y;
    float tubeL = lights[i * 8 + 4].z;
    vec3 tangent = lights[i * 8 + 5].xyz;
    int shadowRef = int(lights[i * 8 + 5].w);
    float cookieFlag = lights[i * 8 + 6].x;
    int iesId = int(lights[i * 8 + 6].y + 0.5);
    int style = int(lights[i * 8 + 6].w + 0.5);

    vec3 lp = fragPos;
    if (style > 0) {
        float unusedAtten = 1.0;
        vec3 unusedColor = vec3(1.0);
        amneticLightStyle(style, AMNETIC_STYLE_POINT, lp, unusedAtten, unusedColor, pos, dir);
    }

    vec3 L;
    float atten;
    if (type == 2) { // directional, no position
        L = -normalize(dir);
        atten = 1.0;
    } else {
        vec3 src = pos; // representative point, area lights move this around
        if (type == 3) { // rect: clamp to the light's local uv extent
            vec3 u = normalize(tangent);
            vec3 v = normalize(cross(dir, u));
            vec3 d = lp - pos;
            src = pos + u * clamp(dot(d, u), -areaW, areaW) + v * clamp(dot(d, v), -areaH, areaH);
        } else if (type == 4) { // disc: clamp to radius
            vec3 n = normalize(dir);
            vec3 d = lp - pos;
            vec3 proj = d - n * dot(d, n);
            float pl = length(proj);
            if (pl > areaW) proj *= areaW / pl;
            src = pos + proj;
        } else if (type == 5) { // tube: nearest point along the capsule axis
            vec3 u = normalize(tangent);
            src = closestSegment(lp, pos - u * tubeL * 0.5, pos + u * tubeL * 0.5);
        }
        vec3 toL = src - lp;
        float dist = length(toL);
        if (dist > range) return vec3(0.0);
        L = toL / max(dist, 1e-4);
        atten = falloff(dist, range, curve, param);
        if (type == 1) { // spot cone attenuation
            vec3 Lc = normalize(pos - lp);
            float cd = dot(-Lc, normalize(dir));
            atten *= clamp((cd - cosOut) / max(cosIn - cosOut, 1e-4), 0.0, 1.0);
        }
    }
    if (iesId > 0 && type != 2) {
        atten *= iesProfile(iesId, dot(normalize(dir), normalize(fragPos - pos)));
    }

    float ndotl = max(dot(N, L), 0.0);
    if (ndotl <= 0.0 || atten <= 0.0) return vec3(0.0);

    vec3 lcol = color;
    if (type == 1 && HasCookie == 1 && cookieFlag > 0.5) {
        lcol *= cookieTint(fragPos, pos, dir, cosOut, tangent);
    }
    if (style > 0) {
        amneticLightStyle(style, AMNETIC_STYLE_SHADE, lp, atten, lcol, pos, dir);
        if (atten <= 0.0) return vec3(0.0);
    }

    vec3 vis = shadowVisibility(shadowRef, fragPos, N, pos, range);
    vis *= contactShadow(fragPos, L);
    if (shadowRef >= 0) {
        float sStrength = lights[i * 8 + 4].w;
        float camDist = length(fragPos);
        if (type == 2) { // sun: fade toward the loosest cascade's reach, no light-position falloff
            float sFade = clamp((SunShadowDistance - camDist) / max(SunShadowDistance * 0.15, 1e-3), 0.0, 1.0);
            vis = mix(vec3(1.0), vis, sStrength * sFade);
        } else {
            float sFade = clamp((ShadowFadeEnd - camDist) / max(ShadowFadeEnd - ShadowFadeStart, 1e-3), 0.0, 1.0);
            float lightDist = length(pos - fragPos);
            float lightFade = 1.0 - smoothstep(range * 0.7, range, lightDist); // shadow fades with the light, not at a hard cutoff
            vis = mix(vec3(1.0), vis, sStrength * sFade * lightFade);
        }
    }
    if (vis.r + vis.g + vis.b <= 0.0) return vec3(0.0);

    vec3 H = normalize(L + V);
    float ndoth = max(dot(N, H), 0.0);
    float shininess = mix(256.0, 4.0, clamp(rough, 0.0, 1.0));
    float spec = pow(ndoth, shininess) * f0;
    specOut += lcol * intens * atten * ndotl * spec * vis;

    // directional sun: the forward base lighting is already full daylight and AlbedoSampler holds the
    // lit scene, not material albedo, so adding sun radiance would double-light everything. instead
    // the sun only carves shadows: it reports how much direct light the shadow removes and main()
    // darkens by that amount. intensity therefore controls shadow depth, not brightness
    if (type == 2) {
        float visL = dot(vis, vec3(1.0 / 3.0));
        sunShadowOut += intens * ndotl * (1.0 - visL);
        return vec3(0.0);
    }

    vec3 radiance = lcol * intens * ndotl * atten * vis;
    return radiance;
}

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
    for (int i = 0; i < LightCount; i++) radiance += lightContribution(i, fragPos, N, V, rough, f0, specular, sunShadow);

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
