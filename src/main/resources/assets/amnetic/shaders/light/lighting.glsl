uniform sampler2D AlbedoSampler;
uniform sampler2D DepthSampler;
uniform sampler2D GNormalSampler;
uniform sampler2D GMaterialSampler; // r = roughness, g = metallic, b = materialId/255, a = (blockLevel*16 + skyLevel)/255
uniform sampler2D GShadeSampler;
uniform sampler2D GAlbedoSampler; // rgb = the material colour a forward pass published, a = whether it did
uniform sampler2D LightmapSampler;
uniform int HasGBuffer;
uniform int ShaderPackComposite;
uniform float SceneAmbient;
uniform int DepthReversed;

bool skyDepth(float depth) {
#ifdef AMNETIC_REVERSED_DEPTH
    return depth <= 0.0;
#else
    return DepthReversed == 1 ? depth <= 0.0 : depth >= 1.0;
#endif
}

vec3 lightMaterial(vec3 scene) {
    // The shader pack has already shaded and tonemapped the image. Keep local lamps
    // visible on dark ground without assuming a pack-specific colortex layout.
    return ShaderPackComposite == 1 ? max(scene, vec3(0.2)) : scene;
}

vec3 compositeLight(vec3 scene, vec3 light) {
    return ShaderPackComposite == 1
        ? max(vec3(1.0) - scene, vec3(0.0)) * (vec3(1.0) - exp(-max(light, vec3(0.0))))
        : light;
}
uniform sampler2D LocalLightSampler;

uniform mat4 InvViewProj;
uniform int ZeroToOne;
uniform int LightCount;
uniform float LightTime; // seconds, wrapping every hour, for styles that move
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

uniform samplerBuffer LightTexels;
uniform samplerBuffer MaterialTexels;

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

// distance in front of the camera in blocks, clip w for perspective and clip z for ortho
float viewDistance(vec3 p) {
    vec3 w = vec3(ViewProj[0][3], ViewProj[1][3], ViewProj[2][3]);
    if (dot(w, w) > 1e-10) return dot(w, p) + ViewProj[3][3];
    vec3 z = vec3(ViewProj[0][2], ViewProj[1][2], ViewProj[2][2]);
    return dot(z, p) / max(length(z), 1e-10);
}

// how far a march point sits behind the depth buffer, huge off screen and very negative over sky
float contactBehind(vec3 p, out vec2 ndc) {
    vec4 clip = ViewProj * vec4(p, 1.0);
    ndc = vec2(2.0);
    if (clip.w <= 0.0) return 1e9;
    ndc = clip.xy / clip.w;
    if (abs(ndc.x) > 1.0 || abs(ndc.y) > 1.0) return 1e9;
    float sd = textureLod(DepthSampler, ndc * 0.5 + 0.5, 0.0).r;
    if (skyDepth(sd)) return -1e9;
    return viewDistance(p) - viewDistance(reconstruct(ndc * 0.5 + 0.5, sd));
}

// screen-space contact shadows, catches small occluders shadow maps miss
// four interleaved marches with a per pixel offset are averaged, so neighbours differ by a quarter step instead of a whole one
float contactShadow(vec3 P, vec3 N, vec3 L) {
    if (ContactSteps <= 0) return 1.0;
    float viewDepth = viewDistance(P);
    // fades out with distance, past that the shadow maps cover it and a march would only alias
    float far = 1.0 - smoothstep(24.0, 40.0, viewDepth);
    if (far <= 0.0) return 1.0;
    // a pixel's footprint grows with depth, so the start push and the bias grow with it
    float pixel = max(viewDepth, 0.1) * 0.002;
    vec3 start = P + N * (0.01 + pixel * 2.0);
    int steps = max(ContactSteps / 2, 3);
    float stepLen = ContactDistance / float(steps);
    float thickness = max(ContactThickness, pixel * 4.0);
    float bias = pixel * 3.0 + stepLen * 0.25;
    float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float shadow = 0.0;
    for (int m = 0; m < 4; m++) {
        float offset = (float(m) + noise) * 0.25;
        float hit = 0.0;
        for (int i = 0; i < steps; i++) {
            float t = (float(i) + offset) / float(steps);
            vec2 ndc;
            float behind = contactBehind(start + L * (ContactDistance * t), ndc);
            if (behind > 1e8) break;
            if (behind > bias && behind < thickness) {
                // a ray grazing an edge counts for little, weaker toward the end of the march and the screen border
                vec2 border = smoothstep(vec2(1.0), vec2(0.9), abs(ndc));
                float solid = smoothstep(bias, bias * 3.0, behind) * (1.0 - smoothstep(thickness * 0.7, thickness, behind));
                hit = max(hit, solid * (1.0 - t) * (1.0 - t) * border.x * border.y);
            }
        }
        shadow += hit;
    }
    return 1.0 - shadow * 0.25 * far;
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
    if (curve == 0) {
        float window = 1.0 - t * t;
        return window * window;
    }
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
    vec3 pos = texelFetch(LightTexels, i * 8 + 0).xyz;
    float range = texelFetch(LightTexels, i * 8 + 0).w;
    vec3 color = texelFetch(LightTexels, i * 8 + 1).rgb;
    float intens = texelFetch(LightTexels, i * 8 + 1).w;
    vec3 dir = texelFetch(LightTexels, i * 8 + 2).xyz;
    int type = int(texelFetch(LightTexels, i * 8 + 2).w + 0.5);
    // Shader packs own their sun and shadow maps; only add Amnetic's local lights.
    if (ShaderPackComposite == 1 && type == 2) return vec3(0.0);
    float cosIn = texelFetch(LightTexels, i * 8 + 3).x;
    float cosOut = texelFetch(LightTexels, i * 8 + 3).y;
    int curve = int(texelFetch(LightTexels, i * 8 + 3).z + 0.5);
    float param = texelFetch(LightTexels, i * 8 + 3).w;
    float areaW = texelFetch(LightTexels, i * 8 + 4).x;
    float areaH = texelFetch(LightTexels, i * 8 + 4).y;
    float tubeL = texelFetch(LightTexels, i * 8 + 4).z;
    vec3 tangent = texelFetch(LightTexels, i * 8 + 5).xyz;
    int shadowRef = int(texelFetch(LightTexels, i * 8 + 5).w);
    float cookieFlag = texelFetch(LightTexels, i * 8 + 6).x;
    int iesId = int(texelFetch(LightTexels, i * 8 + 6).y + 0.5);
    int style = int(texelFetch(LightTexels, i * 8 + 6).w + 0.5);

    if (type != 2 && style == 0) {
        float reach = range + max(max(areaW, areaH), tubeL * 0.5);
        vec3 toPos = pos - fragPos;
        if (dot(toPos, toPos) > reach * reach) return vec3(0.0);
    }

    vec3 lp = fragPos;
    if (style > 0) {
        float unusedAtten = 1.0;
        vec3 unusedColor = vec3(1.0);
        amneticLightStyle(style, AMNETIC_STYLE_POINT, lp, unusedAtten, unusedColor, pos, dir);
    }

    vec3 L;
    float atten;
    float ndotl;
    if (type == 2) { // directional, no position
        L = -normalize(dir);
        ndotl = max(dot(N, L), 0.0);
        if (ndotl <= 0.0) return vec3(0.0);
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
        ndotl = max(dot(N, L), 0.0);
        if (ndotl <= 0.0) return vec3(0.0);
        atten = falloff(dist, range, curve, param);
        if (type == 1) { // spot cone attenuation
            vec3 Lc = normalize(pos - lp);
            float cd = dot(-Lc, normalize(dir));
            atten *= smoothstep(0.0, 1.0, clamp((cd - cosOut) / max(cosIn - cosOut, 1e-4), 0.0, 1.0));
        }
    }
    if (iesId > 0 && type != 2) {
        atten *= iesProfile(iesId, dot(normalize(dir), normalize(fragPos - pos)));
    }

    if (atten <= 0.0) return vec3(0.0);

    vec3 lcol = color;
    if (type == 1 && HasCookie == 1 && cookieFlag > 0.5) {
        lcol *= cookieTint(fragPos, pos, dir, cosOut, tangent);
    }
    if (style > 0) {
        amneticLightStyle(style, AMNETIC_STYLE_SHADE, lp, atten, lcol, pos, dir);
        if (atten <= 0.0) return vec3(0.0);
    }

    vec3 vis = shadowVisibility(shadowRef, fragPos, N, pos, range);
    if (any(notEqual(vis, vec3(0.0)))) vis *= contactShadow(fragPos, N, L);
    if (shadowRef >= 0) {
        float sStrength = texelFetch(LightTexels, i * 8 + 4).w;
        float camDist = length(fragPos);
        if (type == 2) { // sun: fade toward the loosest cascade's reach, no light-position falloff
            float sFade = clamp((SunShadowDistance - camDist) / max(SunShadowDistance * 0.15, 1e-3), 0.0, 1.0);
            vis = mix(vec3(1.0), vis, sStrength * sFade);
        } else {
            float sFade = clamp((ShadowFadeEnd - camDist) / max(ShadowFadeEnd - ShadowFadeStart, 1e-3), 0.0, 1.0);
            vis = mix(vec3(1.0), vis, sStrength * sFade);
        }
    }
    // directional sun: the forward base lighting is already full daylight and AlbedoSampler holds the
    // lit scene, not material albedo, so adding sun radiance would double-light everything. that goes
    // for its highlight too, which otherwise washes a matte floor out to white. instead the sun only
    // carves shadows: it reports how much direct light the shadow removes and main() darkens by that
    // amount. intensity therefore controls shadow depth, not brightness
    if (type == 2) {
        float visL = dot(vis, vec3(1.0 / 3.0));
        // a surface either faces the sun or it does not; scaling by ndotl left shadows half lit
        float facing = smoothstep(0.0, 0.35, ndotl);
        sunShadowOut += intens * facing * (1.0 - visL);
        return vec3(0.0);
    }

    // a fully shadowed pixel still has to be reported above, so this comes after the sun
    if (vis.r + vis.g + vis.b <= 0.0) return vec3(0.0);

    float gloss = 1.0 - smoothstep(0.35, 0.75, rough);
    if (gloss > 0.0) {
        vec3 halfVector = L + V;
        vec3 H = halfVector / max(length(halfVector), 1e-5);
        float ndoth = max(dot(N, H), 0.0);
        float spec = pow(ndoth, mix(96.0, 512.0, gloss)) * f0 * gloss;
        specOut += lcol * intens * atten * ndotl * spec * vis;
    }

    vec3 radiance = lcol * intens * ((ndotl + 0.4) / 1.4) * atten * vis;
    return radiance;
}

vec3 toLinear(vec3 color) {
    color = max(color, vec3(0.0));
    return mix(color / 12.92, pow((color + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), color));
}
vec3 toDisplay(vec3 color) {
    color = max(color, vec3(0.0));
    return mix(color * 12.92, 1.055 * pow(color, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), color));
}
vec3 surfaceRadiance(vec3 material, vec3 irradiance, vec3 specular, float metallic) {
    vec3 reflectance = max(toLinear(material), vec3(0.0001));
    return max(irradiance * (1.0 - metallic)
            + mix(1.0 / reflectance, vec3(1.0), metallic) * specular, vec3(0.0));
}
vec3 resolveSurface(vec3 scene, float sunMultiplier, vec3 material, vec3 local) {
    vec3 base = scene * sunMultiplier * SceneAmbient;
    float peak = max(local.r, max(local.g, local.b));
    if (peak <= 0.0) return base;
    vec3 illumination = local / (1.0 + peak / 2.0);
    return toDisplay(toLinear(base) + toLinear(material) * illumination);
}
