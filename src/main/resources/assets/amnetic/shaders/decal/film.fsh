#version 330 core

layout(location = 0, index = 0) out vec4 AddColor;
layout(location = 0, index = 1) out vec4 Transmit;

#include "amnetic:shaders/decal/film_common.glsl"
#include "amnetic:shaders/decal/film_pixel.glsl"

uniform sampler2D SceneColor;
uniform mat4 ViewProj;
uniform mat4 View;
uniform vec2 ScreenReflect;

float filmLinZ(vec3 p) {
    return -(View * vec4(p, 1.0)).z;
}

vec2 filmProject(vec3 p, out float w) {
    vec4 c = ViewProj * vec4(p, 1.0);
    w = c.w;
    return (c.xy / max(c.w, 1e-5)) * 0.5 + 0.5;
}

vec4 filmTrace(vec3 P, vec3 R, float rough, float NdV) {
    const int STEPS = 14;
    float maxDist = ScreenReflect.y;
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float growth = 1.0 + 3.0 / float(STEPS);
    float stepLen = maxDist / float(STEPS) * 0.25 * (0.6 + 0.4 * jitter);
    vec3 cur = P + R * 0.04;
    vec3 prev = cur;
    float travelled = 0.0;
    float w;
    for (int i = 0; i < STEPS; i++) {
        prev = cur;
        cur += R * stepLen;
        travelled += stepLen;
        stepLen *= growth;
        if (travelled > maxDist) break;
        vec2 suv = filmProject(cur, w);
        if (w <= 0.0 || any(lessThan(suv, vec2(0.0))) || any(greaterThan(suv, vec2(1.0)))) break;
        float sd = texture(DepthSampler, suv).r;
        if (IS_SKY(sd)) continue;
        float sceneZ = filmLinZ(reconstruct(suv, sd));
        if (filmLinZ(cur) > sceneZ) {
            vec3 a = prev, b = cur;
            for (int r = 0; r < 5; r++) {
                vec3 m = (a + b) * 0.5;
                vec2 muv = filmProject(m, w);
                if (filmLinZ(m) > filmLinZ(reconstruct(muv, texture(DepthSampler, muv).r))) b = m; else a = m;
            }
            vec2 huv = filmProject(b, w);
            float gap = filmLinZ(b) - filmLinZ(reconstruct(huv, texture(DepthSampler, huv).r));
            if (gap > 0.35 + travelled * 0.08) return vec4(0.0);
            vec2 blur = vec2(0.0015 + rough * 0.04) * clamp(travelled / 6.0, 0.15, 1.0) * (0.25 + 0.75 * NdV);
            blur.x *= float(textureSize(SceneColor, 0).y) / float(textureSize(SceneColor, 0).x);
            vec3 col = texture(SceneColor, huv).rgb * 0.4
                     + texture(SceneColor, huv + vec2(blur.x, blur.y * 0.5)).rgb * 0.15
                     + texture(SceneColor, huv - vec2(blur.x, blur.y * 0.5)).rgb * 0.15
                     + texture(SceneColor, huv + vec2(-blur.x * 0.5, blur.y)).rgb * 0.15
                     + texture(SceneColor, huv - vec2(-blur.x * 0.5, blur.y)).rgb * 0.15;
            vec2 e = smoothstep(vec2(0.0), vec2(0.08), huv) * (1.0 - smoothstep(vec2(0.92), vec2(1.0), huv));
            float fade = e.x * e.y * (1.0 - smoothstep(maxDist * 0.6, maxDist, travelled));
            return vec4(col, fade);
        }
    }
    vec2 skyUv = filmProject(P + R * 400.0, w);
    if (w > 0.0 && all(greaterThan(skyUv, vec2(0.0))) && all(lessThan(skyUv, vec2(1.0)))
            && IS_SKY(texture(DepthSampler, skyUv).r)) {
        vec2 e = smoothstep(vec2(0.0), vec2(0.1), skyUv) * (1.0 - smoothstep(vec2(0.9), vec2(1.0), skyUv));
        return vec4(texture(SceneColor, skyUv).rgb, e.x * e.y);
    }
    return vec4(0.0);
}

void main() {
    if (PixelArt == 1) {
        filmPixel(AddColor, Transmit);
        return;
    }
    FilmSample s;
    if (!filmSample(s)) discard;

    vec3 absorb = mix(Absorb, AbsorbAged, s.age);
    vec3 liquid = exp(-absorb * s.t);
    vec3 T = mix(vec3(1.0), liquid, s.cov);
    T *= mix(vec3(1.0), StainTint, s.stain);

    float sky = s.sky * s.sky;
    float blk = s.block * s.block;
    vec3 ambient = SkyColor * sky + BlockColor * blk;
    vec3 add = Scatter * (1.0 - liquid.g) * s.cov * ambient;

    float wet = s.wet * s.cov;
    float NdV = max(dot(s.N, s.V), 0.0);
    float F = Reflectance + (1.0 - Reflectance) * pow(1.0 - NdV, 5.0);
    vec3 R = reflect(-s.V, s.N);
    vec3 env = mix(SkyColor * 0.35, SkyColor, clamp(R.y * 2.0, 0.0, 1.0)) * sky + BlockColor * blk * 0.4;
    float rough = mix(Rough.y, Rough.x, s.wet);
    if (ScreenReflect.x > 0.0 && wet * s.mask > 0.02 && R.y > 0.0) {
        vec4 hit = filmTrace(s.P, R, rough, NdV);
        env = mix(env, hit.rgb, hit.a * ScreenReflect.x);
    }
    vec3 H = normalize(SunDir + s.V);
    float NdH = max(dot(s.N, H), 0.0);
    float NdL = max(dot(s.N, SunDir), 0.0);
    float a2 = max(rough * rough, 0.002);
    a2 *= a2;
    float d = NdH * NdH * (a2 - 1.0) + 1.0;
    float D = a2 / (3.14159 * d * d);
    float sunVis = smoothstep(0.6, 1.0, s.sky) * clamp(SunDir.y * 4.0, 0.0, 1.0);
    vec3 spec = SunColor * min(D * F * NdL * 0.25, 8.0) * sunVis * Specular;
    add += (env * F + spec) * wet;
    add += (SkyColor * sky * 1.6 + BlockColor * blk + SunColor * sunVis * 0.5) * s.splash * 0.35;

    T *= 1.0 - F * wet * 0.8;
    T = mix(vec3(1.0), T, s.mask);
    add *= s.mask;
    AddColor = vec4(add, 1.0);
    Transmit = vec4(T, 1.0);
}
