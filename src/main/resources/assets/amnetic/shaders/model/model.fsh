#version 330 core

in vec3 vNormal;
in vec3 vTangent;
in vec3 vBitangent;
in vec2 vUV;
in vec4 vLight;
in vec3 vWorldPos;
in vec3 vMotion;
in vec4 vColor;
noperspective in vec2 vUVFlat;

layout(location = 0) out vec4 FragColor;
layout(location = 1) out vec4 GNormal;
layout(location = 2) out vec4 GMaterial;
layout(location = 3) out vec4 GEmissive;
layout(location = 4) out vec4 GAlbedo;
layout(location = 5) out vec4 GVelocity;
layout(location = 6) out vec4 GShade;

uniform sampler2D AlbedoSampler;
uniform sampler2D NormalSampler;
uniform sampler2D OrmSampler;
uniform sampler2D EmissiveSampler;
uniform sampler2D RoughnessSampler;
uniform sampler2D MetallicSampler;
uniform sampler2D OcclusionSampler;

uniform int HasAlbedo;
uniform int HasNormal;
uniform int HasOrm;
uniform int HasEmissive;
uniform int HasRoughnessMap;
uniform int HasMetallicMap;
uniform int HasOcclusionMap;
uniform int EmissiveEncoded;
uniform int MaterialId;
const int FLAT_SHADED_BASE = 128;

uniform vec4 BaseColor;
uniform vec3 Emissive;
uniform float Metallic;
uniform float Roughness;
uniform float AlphaCutoff;
uniform float EmissiveStrength;
uniform float Transmission;
uniform vec4 UvTransform; // xy repeat, zw offset
uniform int DoubleSided;
uniform float TextureWarp; // 0 perspective correct uvs, 1 affine like a psx
uniform int Unlit; // base colour as is, no light
uniform int AlbedoEncoded; // the base colour map is plain rgb8, not srgb

uniform samplerCube EnvCube; // prefiltered sky/env probe
uniform sampler2D LightMap;
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

uniform float Time;

#include "amnetic:shaders/material/custom_surface_ladder.glsl"
#include "amnetic:shaders/common/look.glsl"



// analytic sky environment, stands in for an HDR IBL probe
vec3 skyEnv(vec3 d) {
    float up = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 zenith = vec3(0.30, 0.50, 0.92);
    vec3 horizon = vec3(0.78, 0.86, 1.00);
    vec3 ground = vec3(0.34, 0.31, 0.27);
    vec3 sky = mix(horizon, zenith, pow(up, 0.55));
    return d.y >= 0.0 ? sky : mix(horizon, ground, clamp(-d.y * 2.5, 0.0, 1.0));
}

vec2 materialUv;

vec3 computeNormal() {
    vec3 n = normalize(vNormal);
    if (HasNormal == 1) {
        vec3 sampled = texture(NormalSampler, materialUv).xyz * 2.0 - 1.0;
        if (dot(vTangent, vTangent) > 0.25) {
            mat3 tbn = mat3(normalize(vTangent), normalize(vBitangent), n);
            return normalize(tbn * sampled);
        }
        vec3 dp1 = dFdx(vWorldPos);
        vec3 dp2 = dFdy(vWorldPos);
        vec2 duv1 = dFdx(materialUv);
        vec2 duv2 = dFdy(materialUv);
        vec3 dp2perp = cross(dp2, n);
        vec3 dp1perp = cross(n, dp1);
        vec3 t = dp2perp * duv1.x + dp1perp * duv2.x;
        vec3 b = dp2perp * duv1.y + dp1perp * duv2.y;
        float scale = inversesqrt(max(max(dot(t, t), dot(b, b)), 1e-20));
        return normalize(mat3(t * scale, -b * scale, n) * sampled);
    }
    return n;
}


void main() {
    GVelocity = vec4(vMotion, 1.0);
    GShade = vec4(0.0);
    materialUv = mix(vUV, vUVFlat, TextureWarp) * UvTransform.xy + UvTransform.zw;
    vec4 albedo = BaseColor * vColor;
    if (HasAlbedo == 1) {
        vec4 sampled = texture(AlbedoSampler, materialUv);
        if (AlbedoEncoded == 1) sampled.rgb = pow(sampled.rgb, vec3(2.2));
        albedo *= sampled;
    }
    if (AlphaCutoff > 0.0 && albedo.a < AlphaCutoff) discard;
    if (AlphaCutoff < 0.0) {
        vec2 pixel = gl_FragCoord.xy + mod(floor(Time * 60.0), 64.0) * 5.588238;
        if (albedo.a <= fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))))) discard;
        albedo.a = 1.0;
    }
    if (albedo.a < 0.003) discard;

    float metallic = Metallic;
    float roughness = Roughness;
    float ao = 1.0;
    if (HasOrm == 1) {
        vec3 orm = texture(OrmSampler, materialUv).rgb; // ORM: r = occlusion, g = roughness, b = metallic
        // glTF metallicRoughness textures leave r unused (0). treating that as occlusion would kill
        // all ambient and render the surface black, so remap r into [0.35, 1]. real packed AO still
        // darkens but a bare MR texture no longer blacks out
        ao = mix(0.35, 1.0, orm.r);
        roughness *= orm.g;
        metallic *= orm.b;
    }
    if (HasOcclusionMap == 1) ao = texture(OcclusionSampler, materialUv).r;
    if (HasRoughnessMap == 1) roughness = texture(RoughnessSampler, materialUv).r;
    if (HasMetallicMap == 1) metallic = texture(MetallicSampler, materialUv).r;
    metallic = clamp(metallic, 0.0, 1.0);
    roughness = clamp(roughness, 0.045, 1.0);

    vec3 N = computeNormal();
    if (DoubleSided == 1 && !gl_FrontFacing) N = -N;

    vec3 surfaceEmission = vec3(0.0);
    if (MaterialId != 0) {
        SurfaceSample surface = SurfaceSample(materialUv, albedo.rgb, albedo.a, vec3(0.0), roughness, metallic, N, vWorldPos, Time);
        surfaceCustomMaterial(MaterialId, surface);
        albedo = vec4(surface.albedo, surface.alpha);
        roughness = clamp(surface.roughness, 0.045, 1.0);
        metallic = clamp(surface.metallic, 0.0, 1.0);
        N = normalize(surface.normal);
        surfaceEmission = surface.emission;
        if (albedo.a < 0.003) discard;
    }
    vec3 dNx = dFdx(N);
    vec3 dNy = dFdy(N);
    float widen = min(0.3 * (dot(dNx, dNx) + dot(dNy, dNy)), 0.2);
    float alpha = roughness * roughness;
    roughness = clamp(sqrt(sqrt(alpha * alpha + widen)), 0.045, 1.0);
    vec3 V = normalize(-vWorldPos);
    vec3 R = reflect(-V, N);
    vec3 L = normalize(SunDirection);

    float sky = clamp(vLight.y, 0.0, 1.0);
    float block = clamp(vLight.x, 0.0, 1.0);
    vec3 daylight = pow(texture(LightMap, vec2(0.5 / 16.0, sky * (15.0 / 16.0) + 0.5 / 16.0)).rgb, vec3(2.2));
    vec3 torch = pow(texture(LightMap, vec2(block * (15.0 / 16.0) + 0.5 / 16.0, 0.5 / 16.0)).rgb, vec3(2.2));
    vec3 sun = SunColor * SunIntensity * LOOK_SUN * smoothstep(-0.05, 0.1, L.y) * sky;

    vec3 around, reflected;
    if (HasEnvCube == 1) {
        around = textureLod(EnvCube, N, EnvMaxLod).rgb;
        reflected = textureLod(EnvCube, R, roughness * EnvMaxLod).rgb;
    } else {
        around = skyEnv(N);
        reflected = mix(skyEnv(R), around, roughness);
    }
    vec3 skyLight = lookSkyIrradiance(around) * AmbientStrength * daylight;
    vec3 reflection = lookSkyReflection(reflected) * EnvIntensity * daylight;

    vec3 emissiveColor = Emissive;
    if (HasEmissive == 1) {
        vec3 glow = texture(EmissiveSampler, materialUv).rgb;
        emissiveColor *= EmissiveEncoded == 1 ? pow(glow, vec3(2.2)) : glow;
    }
    vec3 emissive = emissiveColor * EmissiveStrength + surfaceEmission;

    float emStrength = clamp(max(max(emissive.r, emissive.g), emissive.b), 0.0, 1.0);
    float materialIdNorm = float(MaterialId) / 255.0;
    float blockIdx = floor(block * 15.0 + 0.5);
    float skyIdx = floor(sky * 15.0 + 0.5);
    float lightmapEncoded = (blockIdx * 16.0 + skyIdx) / 255.0;
    GNormal = vec4(N, 1.0);
    GAlbedo = vec4(albedo.rgb, 1.0);

    if (Unlit == 1) {
        // black published albedo keeps local lights off it, no shares keeps shadows and ao off
        FragColor = vec4(pow(max(albedo.rgb + emissive, 0.0), vec3(1.0 / 2.2)), albedo.a);
        GAlbedo = vec4(0.0, 0.0, 0.0, 1.0);
        GMaterial = vec4(1.0, 0.0, materialIdNorm, lightmapEncoded);
        GEmissive = vec4(0.0);
        GShade = vec4(0.0, 0.0, 1.0, 1.0);
        return;
    }

    if (MaterialId >= FLAT_SHADED_BASE) {
        vec3 n = normalize(N);
        float face = abs(n.y) > max(abs(n.x), abs(n.z))
                ? (n.y > 0.0 ? 1.0 : 0.5)
                : (abs(n.z) > abs(n.x) ? 0.8 : 0.6);
        vec3 light = texture(LightMap, vec2(block * (15.0 / 16.0) + 0.5 / 16.0, sky * (15.0 / 16.0) + 0.5 / 16.0)).rgb;
        vec3 lamp = texture(LightMap, vec2(block * (15.0 / 16.0) + 0.5 / 16.0, 0.5 / 16.0)).rgb;
        FragColor = vec4(pow(albedo.rgb, vec3(1.0 / 2.2)) * face * light, albedo.a);
        GMaterial = vec4(1.0, 0.0, materialIdNorm, lightmapEncoded);
        GEmissive = vec4(0.0);
        GShade = vec4(lookLuma(max(light - lamp, 0.0)) / max(lookLuma(light), 1e-4), 1.0, 1.0, 1.0);
        return;
    }

    vec4 shares;
    vec3 color = lookSurface(albedo.rgb, metallic, roughness, N, V, L, sun, skyLight, reflection, torch, ao, emissive,
            Exposure * LOOK_EXPOSURE, shares);
    GMaterial = vec4(roughness, metallic, materialIdNorm, lightmapEncoded);
    GEmissive = vec4(emissive, emStrength);
    GShade = shares;

    if (Transmission > 0.0) {
        float frost = roughness;
        vec4 unused;
        vec3 shine = lookSurface(vec3(0.0), 1.0, roughness, N, V, L, sun, vec3(0.0), reflection, vec3(0.0), 1.0,
                vec3(0.0), Exposure * LOOK_EXPOSURE, unused);
        vec3 glass = shine + color * frost + albedo.rgb * 0.08 * lookLuma(daylight + torch) * Exposure * LOOK_EXPOSURE;
        float a = clamp((1.0 - Transmission) + frost * 0.85 + lookLuma(shine) * 0.6, 0.05, 1.0);
        FragColor = vec4(Tonemap == 1 ? lookDisplay(glass) : pow(max(glass, 0.0), vec3(1.0 / 2.2)), a);
        return;
    }

    FragColor = vec4(Tonemap == 1 ? lookDisplay(color) : pow(max(color, 0.0), vec3(1.0 / 2.2)), albedo.a);
}
