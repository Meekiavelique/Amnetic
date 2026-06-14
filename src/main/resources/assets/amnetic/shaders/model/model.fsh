#version 330 core

in vec3 vNormal;
in vec2 vUV;
in vec4 vLight;

out vec4 FragColor;

uniform sampler2D AlbedoSampler;
uniform int HasAlbedo;          // 1 if a base-color texture is bound
uniform vec4 BaseColor;         // PBR base color factor (rgba)
uniform vec3 Emissive;          // PBR emissive factor
uniform float Metallic;         // PBR metallic factor (used to tint the diffuse darker)
uniform float Roughness;        // PBR roughness factor (reserved for specular work)

void main() {
    vec4 albedo = BaseColor;
    if (HasAlbedo == 1) albedo *= texture(AlbedoSampler, vUV);
    if (albedo.a < 0.01) discard;

    float block = clamp(vLight.x, 0.0, 1.0);
    float sky   = clamp(vLight.y, 0.0, 1.0);
    vec3 blockTint = vec3(1.00, 0.90, 0.74);
    vec3 skyTint   = vec3(0.86, 0.92, 1.00);
    vec3 mcLight = max(block * blockTint, sky * skyTint);

    vec3 N = normalize(vNormal);
    float hemi = N.y * 0.5 + 0.5;            // up = brighter
    float shape = mix(0.78, 1.0, hemi);

    vec3 diffuse = albedo.rgb * mix(1.0, 0.35, clamp(Metallic, 0.0, 1.0));

    vec3 ambient = vec3(0.06);
    vec3 lit = diffuse * (ambient + mcLight * shape);
    lit += Emissive;

    FragColor = vec4(lit, albedo.a);
}
