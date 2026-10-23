const float LOOK_PI = 3.14159265359;
const float LOOK_SUN = 2.0;
const float LOOK_SKY = 0.4;
const float LOOK_EXPOSURE = 0.5;
const float LOOK_SKY_REFERENCE = 0.6;
const float LOOK_REFLECTION = 0.7;

vec3 lookNeutral(vec3 color) {
    float low = min(color.r, min(color.g, color.b));
    color -= low < 0.08 ? low - 6.25 * low * low : 0.04;
    float peak = max(color.r, max(color.g, color.b));
    if (peak < 0.76) return color;
    float fresh = 1.0 - 0.0576 / (peak - 0.52);
    color *= fresh / peak;
    return mix(color, vec3(fresh), 1.0 - 1.0 / (0.15 * (peak - fresh) + 1.0));
}

vec3 lookDisplay(vec3 hdr) {
    return pow(clamp(lookNeutral(max(hdr, 0.0)), 0.0, 1.0), vec3(1.0 / 2.2));
}

float lookLuma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float lookShare(vec3 without, vec3 all) {
    return clamp(1.0 - lookLuma(lookDisplay(without)) / max(lookLuma(lookDisplay(all)), 1e-4), 0.0, 1.0);
}

vec3 lookSkyIrradiance(vec3 around) {
    return around * (LOOK_SKY / LOOK_SKY_REFERENCE);
}

vec3 lookSkyReflection(vec3 env) {
    return env * (LOOK_REFLECTION / LOOK_EXPOSURE);
}

vec3 lookSurface(vec3 base, float metallic, float rough, vec3 N, vec3 V, vec3 L, vec3 sun, vec3 sky,
                 vec3 reflection, vec3 torch, float occlusion, vec3 glow, float exposure, out vec4 shares) {
    float NoV = max(dot(N, V), 1e-4);
    float NoL = max(dot(N, L), 0.0);
    vec3 H = normalize(V + L);
    float NoH = max(dot(N, H), 0.0);
    vec3 f0 = mix(vec3(0.04), base, metallic);

    float a = rough * rough;
    float a2 = a * a;
    float d = NoH * NoH * (a2 - 1.0) + 1.0;
    float D = a2 / max(LOOK_PI * d * d, 1e-7);
    float k = (rough + 1.0) * (rough + 1.0) / 8.0;
    float G = NoV / (NoV * (1.0 - k) + k) * NoL / (NoL * (1.0 - k) + k);
    vec3 F = f0 + (1.0 - f0) * pow(clamp(1.0 - max(dot(V, H), 0.0), 0.0, 1.0), 5.0);
    vec3 kd = (1.0 - F) * (1.0 - metallic);
    vec3 direct = (kd * base + LOOK_PI * D * G * F / max(4.0 * NoV * NoL, 1e-4)) * sun * NoL;

    vec3 Fr = f0 + (max(vec3(1.0 - rough), f0) - f0) * pow(clamp(1.0 - NoV, 0.0, 1.0), 5.0);
    vec4 r = rough * vec4(-1.0, -0.0275, -0.572, 0.022) + vec4(1.0, 0.0425, 1.04, -0.04);
    vec2 brdf = vec2(-1.04, 1.04) * (min(r.x * r.x, exp2(-9.28 * NoV)) * r.x + r.y) + r.zw;
    vec3 response = f0 * brdf.x + brdf.y;
    vec3 diffuse = base * (1.0 - Fr) * (1.0 - metallic);
    vec3 skyLight = (sky * diffuse + reflection * response) * occlusion;
    vec3 torchLight = (diffuse + response) * torch * occlusion;

    vec3 color = (direct + skyLight + torchLight + glow) * exposure;
    shares = vec4(lookShare((direct + torchLight + glow) * exposure, color),
            lookShare((skyLight + torchLight + glow) * exposure, color), 1.0, 1.0);
    return color;
}
