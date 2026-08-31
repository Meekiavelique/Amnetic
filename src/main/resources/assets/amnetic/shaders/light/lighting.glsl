uniform sampler2D AlbedoSampler;
uniform sampler2D DepthSampler;
uniform sampler2D GNormalSampler;
uniform sampler2D GMaterialSampler; // r = roughness, g = metallic, b = materialId/255, a = (blockLevel*16 + skyLevel)/255
uniform sampler2D LightmapSampler;
uniform int HasGBuffer;

uniform mat4 InvViewProj;
uniform int ZeroToOne;
uniform int LightCount;
uniform int SkipLocalLights;
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
