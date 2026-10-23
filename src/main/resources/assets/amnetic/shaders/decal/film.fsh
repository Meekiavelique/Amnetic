#version 330 core

// dual source: the added light goes to index 0, how much of the surface shows through goes to index 1
layout(location = 0, index = 0) out vec4 AddColor;
layout(location = 0, index = 1) out vec4 Transmit;

#include "amnetic:shaders/decal/film_common.glsl"
#include "amnetic:shaders/decal/film_pixel.glsl"

void main() {
    if (PixelArt == 1) {
        filmPixel(AddColor, Transmit);
        return;
    }
    FilmSample s;
    if (!filmSample(s)) discard;

    // beer lambert through the liquid, ageing shifts it from bright red to brown
    vec3 absorb = mix(Absorb, AbsorbAged, s.age);
    vec3 liquid = exp(-absorb * s.t);
    vec3 T = mix(vec3(1.0), liquid, s.cov);
    T *= mix(vec3(1.0), StainTint, s.stain);

    // light around this spot, from the texel's light levels
    float sky = s.sky * s.sky;
    float blk = s.block * s.block;
    vec3 ambient = SkyColor * sky + BlockColor * blk;
    vec3 add = Scatter * (1.0 - liquid.g) * s.cov * ambient;

    // the wet top reflects, fresnel by view angle, a sharp sun glint where the sky reaches it
    float wet = s.wet * s.cov;
    float NdV = max(dot(s.N, s.V), 0.0);
    float F = Reflectance + (1.0 - Reflectance) * pow(1.0 - NdV, 5.0);
    vec3 R = reflect(-s.V, s.N);
    vec3 env = mix(SkyColor * 0.35, SkyColor, clamp(R.y * 2.0, 0.0, 1.0)) * sky + BlockColor * blk * 0.4;
    float rough = mix(Rough.y, Rough.x, s.wet);
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

    // what gets reflected doesn't also come through from underneath
    T *= 1.0 - F * wet * 0.8;
    T = mix(vec3(1.0), T, s.mask);
    add *= s.mask;
    AddColor = vec4(add, 1.0);
    Transmit = vec4(T, 1.0);
}
