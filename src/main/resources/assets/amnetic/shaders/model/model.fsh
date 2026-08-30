#version 330 core

in vec3 vNormal;
in vec3 vTangent;
in vec3 vBitangent;
in vec2 vUV;
in vec4 vLight;
in vec3 vWorldPos;

layout(location = 0) out vec4 FragColor;
layout(location = 1) out vec4 GNormal;
layout(location = 2) out vec4 GMaterial;
layout(location = 3) out vec4 GEmissive;

uniform sampler2D AlbedoSampler;
uniform sampler2D NormalSampler;
uniform sampler2D OrmSampler;
uniform sampler2D EmissiveSampler;

uniform int HasAlbedo;
uniform int HasNormal;
uniform int HasOrm;
uniform int HasEmissive;
uniform int MaterialId;
const int FLAT_SHADED_BASE = 128;

uniform vec4 BaseColor;
uniform vec3 Emissive;
uniform float Metallic;
uniform float Roughness;
uniform float AlphaCutoff;
uniform float EmissiveStrength;
uniform float Transmission;

uniform samplerCube EnvCube; // prefiltered sky/env probe
uniform int HasEnvCube;
uniform float EnvMaxLod;

// configurable lighting, see ModelLighting. defaults match the old hardcoded values
uniform vec3 SunDirection;
uniform vec3 SunColor;
uniform float SunIntensity;
uniform float AmbientStrength;
uniform float EnvIntensity;
uniform float Exposure;
uniform int Tonemap;

const float PI = 3.14159265359;

// cook-torrance terms
float distributionGGX(float NoH, float rough) {
    float a = rough * rough;
    float a2 = a * a;
    float d = NoH * NoH * (a2 - 1.0) + 1.0;
    return a2 / max(PI * d * d, 1e-7);
}
float geometrySmith(float NoV, float NoL, float rough) {
    float k = (rough + 1.0);
    k = k * k / 8.0;
    float gv = NoV / (NoV * (1.0 - k) + k);
    float gl = NoL / (NoL * (1.0 - k) + k);
    return gv * gl;
}
vec3 fresnelSchlick(float c, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - c, 0.0, 1.0), 5.0);
}
vec3 fresnelSchlickRough(float c, vec3 F0, float rough) {
    vec3 Fr = max(vec3(1.0 - rough), F0);
    return F0 + (Fr - F0) * pow(clamp(1.0 - c, 0.0, 1.0), 5.0);
}
// karis split-sum analytic environment BRDF, no LUT needed
vec2 envBRDFApprox(float rough, float NoV) {
    const vec4 c0 = vec4(-1.0, -0.0275, -0.572, 0.022);
    const vec4 c1 = vec4(1.0, 0.0425, 1.04, -0.04);
    vec4 r = rough * c0 + c1;
    float a004 = min(r.x * r.x, exp2(-9.28 * NoV)) * r.x + r.y;
    return vec2(-1.04, 1.04) * a004 + r.zw;
}

// analytic sky environment, stands in for an HDR IBL probe
vec3 skyEnv(vec3 d) {
    float up = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 zenith = vec3(0.30, 0.50, 0.92);
    vec3 horizon = vec3(0.78, 0.86, 1.00);
    vec3 ground = vec3(0.34, 0.31, 0.27);
    vec3 sky = mix(horizon, zenith, pow(up, 0.55));
    return d.y >= 0.0 ? sky : mix(horizon, ground, clamp(-d.y * 2.5, 0.0, 1.0));
}

vec3 computeNormal() {
    vec3 n = normalize(vNormal);
    if (HasNormal == 1) {
        vec3 sampled = texture(NormalSampler, vUV).xyz * 2.0 - 1.0;
        mat3 tbn = mat3(normalize(vTangent), normalize(vBitangent), n);
        return normalize(tbn * sampled);
    }
    return n;
}

vec3 acesFilm(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec4 albedo = BaseColor;
    if (HasAlbedo == 1) albedo *= texture(AlbedoSampler, vUV);
    if (AlphaCutoff > 0.0 && albedo.a < AlphaCutoff) discard;
    if (albedo.a < 0.003) discard;

    float metallic = Metallic;
    float roughness = Roughness;
    float ao = 1.0;
    if (HasOrm == 1) {
        vec3 orm = texture(OrmSampler, vUV).rgb; // ORM: r = occlusion, g = roughness, b = metallic
        // glTF metallicRoughness textures leave r unused (0). treating that as occlusion would kill
        // all ambient and render the surface black, so remap r into [0.35, 1]. real packed AO still
        // darkens but a bare MR texture no longer blacks out
        ao = mix(0.35, 1.0, orm.r);
        roughness *= orm.g;
        metallic *= orm.b;
    }
    metallic = clamp(metallic, 0.0, 1.0);
    roughness = clamp(roughness, 0.045, 1.0);

    vec3 N = computeNormal();
    vec3 V = normalize(-vWorldPos);
    float NoV = max(dot(N, V), 1e-4);
    vec3 R = reflect(-V, N);
    vec3 F0 = mix(vec3(0.04), albedo.rgb, metallic);

    float sky = clamp(vLight.y, 0.0, 1.0);
    float block = clamp(vLight.x, 0.0, 1.0);
    // keep some environment indoors so shaded interiors are lit
    float envScale = mix(0.45, 1.0, sky) * EnvIntensity;

    // direct sun
    vec3 L = normalize(SunDirection);
    vec3 H = normalize(V + L);
    float NoL = max(dot(N, L), 0.0);
    float NoH = max(dot(N, H), 0.0);
    float VoH = max(dot(V, H), 0.0);
    vec3 sunColor = SunColor * (0.6 + 1.6 * sky) * SunIntensity;
    float D = distributionGGX(NoH, roughness);
    float G = geometrySmith(NoV, NoL, roughness);
    vec3 Fd = fresnelSchlick(VoH, F0);
    vec3 sunSpec = (D * G * Fd) / max(4.0 * NoV * NoL, 1e-4);
    vec3 sunKd = (vec3(1.0) - Fd) * (1.0 - metallic);
    vec3 direct = (sunKd * albedo.rgb / PI + sunSpec) * sunColor * NoL;

    // IBL: prefer the baked prefiltered env cubemap (roughness maps to mip for correct glossy
    // reflections), fall back to the inline analytic sky if the probe isn't ready
    vec3 irradiance, prefiltered;
    if (HasEnvCube == 1) {
        irradiance = textureLod(EnvCube, N, EnvMaxLod).rgb * envScale + vec3(0.05) * block;
        prefiltered = textureLod(EnvCube, R, roughness * EnvMaxLod).rgb * envScale;
    } else {
        irradiance = skyEnv(N) * envScale + vec3(0.05) * block;
        prefiltered = mix(skyEnv(R), skyEnv(N), roughness) * envScale;
    }
    vec3 ambientLightScale = vec3(AmbientStrength);
    irradiance *= ambientLightScale;
    prefiltered *= ambientLightScale;
    vec3 Fr = fresnelSchlickRough(NoV, F0, roughness);
    vec2 brdf = envBRDFApprox(roughness, NoV);
    vec3 kdIbl = (vec3(1.0) - Fr) * (1.0 - metallic);
    vec3 iblDiffuse = irradiance * albedo.rgb * kdIbl;
    vec3 iblSpecular = prefiltered * (Fr * brdf.x + brdf.y);
    vec3 ambient = (iblDiffuse + iblSpecular) * ao;

    vec3 emissiveColor = Emissive;
    if (HasEmissive == 1) emissiveColor *= texture(EmissiveSampler, vUV).rgb;
    vec3 emissive = emissiveColor * EmissiveStrength;

    float emStrength = clamp(max(max(emissiveColor.r, emissiveColor.g), emissiveColor.b) * EmissiveStrength, 0.0, 1.0);
    float materialIdNorm = float(MaterialId) / 255.0;
    float blockIdx = floor(clamp(vLight.x, 0.0, 1.0) * 15.0 + 0.5);
    float skyIdx = floor(clamp(vLight.y, 0.0, 1.0) * 15.0 + 0.5);
    float lightmapEncoded = (blockIdx * 16.0 + skyIdx) / 255.0;

    if (MaterialId >= FLAT_SHADED_BASE) {
        vec3 n = normalize(N);
        float face = abs(n.y) > max(abs(n.x), abs(n.z))
                ? (n.y > 0.0 ? 1.0 : 0.5)
                : (abs(n.z) > abs(n.x) ? 0.8 : 0.6);
        float lm = clamp(max(sky, block), 0.03, 1.0);
        FragColor = vec4(albedo.rgb * face * lm, albedo.a);
        GNormal = vec4(N, 1.0);
        // roughness 1, metallic 0: nothing downstream should put a highlight on this
        GMaterial = vec4(1.0, 0.0, materialIdNorm, lightmapEncoded);
        GEmissive = vec4(0.0);
        return;
    }

    // transmissive glass, rough transmission where the roughness map doubles as the dirt/frost mask
    if (Transmission > 0.0) {
        float frost = roughness;
        // clean glass: crisp sky reflection + faint tint, mostly see-through. dirty: opaque whitish frost
        vec3 reflectCol = sunSpec * sunColor * NoL + iblSpecular;
        vec3 frostCol = (irradiance * 0.6 + 0.06) * ao;
        float glassLm = clamp(max(sky, block), 0.03, 1.0);
        vec3 glass = ((reflectCol + frostCol * frost + albedo.rgb * 0.08) * glassLm + emissive) * Exposure;
        float reflMax = max(max(reflectCol.r, reflectCol.g), reflectCol.b);
        float a = clamp((1.0 - Transmission) + frost * 0.85 + reflMax * 0.6, 0.05, 1.0);
        if (Tonemap == 1) glass = acesFilm(glass);
        glass = pow(max(glass, 0.0), vec3(1.0 / 2.2));
        FragColor = vec4(glass, a);
        GNormal = vec4(N, 1.0);
        GMaterial = vec4(roughness, metallic, materialIdNorm, lightmapEncoded);
        GEmissive = vec4(emissive, emStrength);
        return;
    }

    // scale by minecraft's lightmap so models track day/night and shade like vanilla geometry,
    // emissive still glows in the dark
    float lightmap = clamp(max(sky, block), 0.03, 1.0);
    vec3 color = ((direct + ambient) * lightmap + emissive) * Exposure;
    if (Tonemap == 1) color = acesFilm(color); // filmic tonemap, toggleable
    color = pow(max(color, 0.0), vec3(1.0 / 2.2)); // linear to sRGB
    FragColor = vec4(color, albedo.a);

    GNormal = vec4(N, 1.0);
    GMaterial = vec4(roughness, metallic, materialIdNorm, lightmapEncoded);
    GEmissive = vec4(emissive, emStrength);
}
