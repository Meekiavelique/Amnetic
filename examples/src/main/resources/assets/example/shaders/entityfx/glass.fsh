#version 330
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:projection.glsl>

uniform sampler2D SceneColorSampler;
uniform sampler2D DepthSampler;
uniform sampler2D UniformSampler;

in vec3 vViewPos;
in vec3 vViewNormal;

out vec4 fragColor;

float linearDepth(float d, mat4 invP) {
    float z = d * 2.0 - 1.0;
    return 1.0 / (z * invP[2].w + invP[3].w);
}

float schlickFresnel(float ior1, float ior2, vec3 view, vec3 norm) {
    float incident = max(dot(view, norm), 1e-3);
    float r = (ior2 - ior1) / (ior2 + ior1);
    r *= r;
    float f = r + (1.0 - r) * pow(1.0 - incident, 5.0);
    return clamp(f, 0.0, 1.0);
}

const float EDGE_THICKNESS = 0.20;
const float REFRACT_SCALE   = 2.6;
const float ABSORB_DENSITY  = 3.2;
const float EDGE_GLOW        = 0.45;

void main() {
    vec4 tint = texelFetch(UniformSampler, ivec2(0, 0), 0);
    vec4 p = texelFetch(UniformSampler, ivec2(1, 0), 0);
    float airIor = 1.0;
    float ior = max(p.r * 4.0, 1.01);
    float refractAmount = p.g;
    float fade = p.a;

    vec3 N = normalize(vViewNormal);
    if (!gl_FrontFacing) N = -N;
    vec3 V = normalize(-vViewPos);

    mat4 invP = inverse(ProjMat);
    vec2 screenUv = gl_FragCoord.xy / ScreenSize;

    float sceneDepth = linearDepth(texture(DepthSampler, screenUv).r, invP);
    float selfDepth = -vViewPos.z;
    float depthDiff = max(sceneDepth - selfDepth, 0.0);

    float thickness = mix(EDGE_THICKNESS, 1.0, abs(N.z));

    float xMult = ScreenSize.y / ScreenSize.x;
    float depthGate = clamp(depthDiff / max(sceneDepth, 1e-3), 0.0, 1.0);
    vec2 offset = N.xy * vec2(xMult, 1.0) * refractAmount * REFRACT_SCALE * thickness * depthGate;
    vec2 refractUv = clamp(screenUv + offset, vec2(0.001), vec2(0.999));

    float newDepth = linearDepth(texture(DepthSampler, refractUv).r, invP);
    if (newDepth < selfDepth) refractUv = screenUv;

    vec3 absorb = exp(-(1.0 - tint.rgb) * tint.a * thickness * ABSORB_DENSITY);
    vec3 refracted = texture(SceneColorSampler, refractUv).rgb * absorb;

    float fres = schlickFresnel(airIor, ior, V, N);
    vec3 reflected = texture(SceneColorSampler, clamp(screenUv - offset, vec2(0.001), vec2(0.999))).rgb;
    reflected = mix(reflected, reflected + tint.rgb * 0.15, 0.5);
    reflected += tint.rgb * fres * EDGE_GLOW;

    vec3 color = mix(refracted, reflected, fres);
    float alpha = clamp(tint.a * 0.35 + thickness * 0.35 + fres * 0.6 + 0.12, 0.0, 1.0) * fade;

    fragColor = vec4(color, alpha);
}
