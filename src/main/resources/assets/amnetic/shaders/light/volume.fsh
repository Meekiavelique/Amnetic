#version 430 core

out vec4 FragColor;

uniform int LightIndex;
uniform vec2 ScreenSize;

#include "amnetic:shaders/light/lighting.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float depth = texture(DepthSampler, uv).r;
    if (depth >= 1.0) discard;

    vec3 fragPos = reconstruct(uv, depth);

    vec3 N;
    float rough = 0.7;
    float f0 = 0.04;
    if (HasGBuffer == 1) {
        vec4 gn = texture(GNormalSampler, uv);
        N = (gn.a > 0.5) ? normalize(gn.xyz) : betterNormal(uv, fragPos);
        if (gn.a > 0.5) {
            vec4 gm = texture(GMaterialSampler, uv);
            rough = gm.x;
            f0 = mix(0.04, 1.0, gm.y);
        }
    } else {
        N = betterNormal(uv, fragPos);
    }

    vec3 V = normalize(-fragPos);
    vec3 albedo = texture(AlbedoSampler, uv).rgb;

    vec3 specular = vec3(0.0);
    float sunShadow = 0.0;
    vec3 radiance = lightContribution(LightIndex, fragPos, N, V, rough, f0, specular, sunShadow);

    vec3 lit = albedo * radiance + specular;
    lit = max(lit, vec3(0.0));
    if (any(isnan(lit)) || any(isinf(lit))) lit = vec3(0.0);
    FragColor = vec4(lit, 1.0);
}
