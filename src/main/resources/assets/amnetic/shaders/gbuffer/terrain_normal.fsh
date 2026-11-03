#version 410 core

in vec2 vUV;
in vec3 vNormal;
in vec3 vPos;

layout(location = 1) out vec4 GNormal;
layout(location = 2) out vec4 GMaterial;

uniform sampler2D BlockAtlas;
uniform sampler2D NormalAtlas;
uniform sampler2D SpecularAtlas;
uniform int AlphaTest;
uniform int HasPbr;

vec3 perturb(vec3 n, vec3 tangentNormal) {
    vec3 dp1 = dFdx(vPos);
    vec3 dp2 = dFdy(vPos);
    vec2 du1 = dFdx(vUV);
    vec2 du2 = dFdy(vUV);
    vec3 dp2perp = cross(dp2, n);
    vec3 dp1perp = cross(n, dp1);
    vec3 t = dp2perp * du1.x + dp1perp * du2.x;
    vec3 b = dp2perp * du1.y + dp1perp * du2.y;
    float scale = inversesqrt(max(max(dot(t, t), dot(b, b)), 1e-20));
    return normalize(mat3(t * scale, -b * scale, n) * tangentNormal);
}

void main() {
    if (AlphaTest == 1 && texture(BlockAtlas, vUV).a < 0.1) discard;
    vec3 n = normalize(vNormal);
    float roughness = 0.9;
    float metallic = 0.0;
    if (HasPbr == 1) {
        vec4 nm = texture(NormalAtlas, vUV);
        if (nm.a > 0.5) {
            vec2 xy = nm.rg * 2.0 - 1.0;
            n = perturb(n, vec3(xy, sqrt(max(1.0 - dot(xy, xy), 0.0))));
        }
        vec4 sp = texture(SpecularAtlas, vUV);
        if (sp.a > 0.5) {
            roughness = (1.0 - sp.r) * (1.0 - sp.r);
            metallic = sp.g >= 229.5 / 255.0 ? 1.0 : 0.0;
        }
    }
    GNormal = vec4(n, 1.0);
    GMaterial = vec4(roughness, metallic, 0.0, 0.0);
}
