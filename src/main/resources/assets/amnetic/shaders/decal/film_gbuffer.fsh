#version 330 core

layout(location = 1) out vec4 OutNormal;
layout(location = 2) out vec4 OutMaterial;

#include "amnetic:shaders/decal/film_common.glsl"

void main() {
    FilmSample s;
    if (!filmSample(s)) discard;
    float w = s.wet * s.cov * s.mask;
    if (w < 0.01) discard;
    float rough = mix(Rough.y, Rough.x, s.wet);
    OutNormal = vec4(s.N, w);
    OutMaterial = vec4(rough, 0.0, 0.0, w);
}
