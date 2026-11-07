#version 410 core

in vec2 vUV;
out vec4 FragColor;

#include "amnetic:shaders/light/lighting.glsl"
uniform int TileLighting;
uniform int TileColumns;
uniform int TileRows;
uniform int TileDepthSlices;
uniform vec4 TileViewDepth;
uniform isamplerBuffer TileLights;
uniform int ScatteringClip;
uniform vec4 ScatteringRect;

// forward scattering glow toward the light source
float hgPhase(float cosT, float g) {
    float g2 = g * g;
    float denom = max(1.0 + g2 - 2.0 * g * cosT, 1e-4);
    return (1.0 - g2) / (4.0 * 3.14159265 * denom * sqrt(denom));
}

// smoother than hash noise, plays nicer with temporal accumulation
const float BAYER4[16] = float[16](
0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
float bayerDither() {
    int x = int(mod(gl_FragCoord.x, 4.0));
    int y = int(mod(gl_FragCoord.y, 4.0));
    return (BAYER4[y * 4 + x] + 0.5) / 16.0;
}

// far bound for rays that hit no geometry. per-light sphere clipping decides the real march
// interval, so this only has to be past every light's reach
const float SKY_RAY_DISTANCE = 8192.0;

bool clipSpotCone(vec3 origin, vec3 ray, vec3 axis, float cosine, inout float nearT, inout float farT) {
    float h = dot(origin, axis), dh = dot(ray, axis);
    if (abs(dh) < 1e-6) { if (h < 0.0) return false; }
    else if (dh > 0.0) nearT = max(nearT, -h / dh);
    else farT = min(farT, -h / dh);
    if (farT <= nearT) return false;
    float c2 = cosine * cosine;
    float a = c2 - dh * dh;
    float b = 2.0 * (c2 * dot(origin, ray) - h * dh);
    float c = c2 * dot(origin, origin) - h * h;
    if (abs(a) < 1e-6) {
        if (abs(b) < 1e-6) return c <= 0.0;
        if (b > 0.0) farT = min(farT, -c / b); else nearT = max(nearT, -c / b);
    } else {
        float discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0.0) return a < 0.0;
        float root = sqrt(discriminant);
        float t0 = (-b - root) / (2.0 * a), t1 = (-b + root) / (2.0 * a);
        float low = min(t0, t1), high = max(t0, t1);
        if (a > 0.0) { nearT = max(nearT, low); farT = min(farT, high); }
        else if (nearT < low) farT = min(farT, low);
        else nearT = max(nearT, high);
    }
    return farT > nearT;
}

float spotRayVisibility(vec4 projected, vec2 tileOffset) {
    if (projected.w <= 0.0) return 1.0;
    vec3 ndc = projected.xyz / projected.w;
    if (abs(ndc.x) > 1.0 || abs(ndc.y) > 1.0 || ndc.z > 1.0) return 1.0;
    vec2 uv = ndc.xy * 0.5 + 0.5;
    float stored = textureLod(SpotShadowAtlas, (tileOffset + clamp(uv * ShadowTileSize, vec2(0.5), vec2(ShadowTileSize - 0.5))) / ShadowAtlasSize, 0.0).r;
    float bias = ShadowBias * min(1.0, 5.0 / max(projected.w * projected.w, 1e-4));
    return ndc.z * 0.5 + 0.5 - bias <= stored ? 1.0 : 0.0;
}

// volumetric light shafts, only marches inside each light's sphere so steps aren't wasted
// light needs shadow casting on for the beam to be blocked by geometry
vec3 volumetric(vec3 fragPos) {
    float segLen = length(fragPos);
    if (segLen < 1e-3 || VolumetricSteps <= 0 || VolumetricStrength <= 0.0 || VolumetricDensity <= 0.0) return vec3(0.0);
    vec3 rayDir = fragPos / segLen;
    float dither = TemporalOffset < 0.0 ? 0.5 : fract(bayerDither() + TemporalOffset);

    vec3 result = vec3(0.0);
    for (int i = 0; i < LightCount; i++) {
        int type = int(texelFetch(LightTexels, i * 8 + 2).w + 0.5);
        float gstr = texelFetch(LightTexels, i * 8 + 6).z; // per-light strength (0 = no shaft)
        if (gstr <= 0.0) continue;

        // per-light volumetric params (lane 7)
        int baseSteps = min(VolumetricSteps, int(texelFetch(LightTexels, i * 8 + 7).x + 0.5));
        float density = texelFetch(LightTexels, i * 8 + 7).y * VolumetricDensity;
        float aniso = min(VolumetricAniso, texelFetch(LightTexels, i * 8 + 7).z);
        bool lShadows = VolumetricShadows == 1 && texelFetch(LightTexels, i * 8 + 7).w > 0.5;
        if (baseSteps <= 0) continue;

        vec3 pos = texelFetch(LightTexels, i * 8 + 0).xyz;
        float range = texelFetch(LightTexels, i * 8 + 0).w;

        // clip the ray to the light's sphere so we only march where it matters
        vec3 oc = -pos;
        float b = dot(oc, rayDir);
        float c = dot(oc, oc) - range * range;
        float disc = b * b - c;
        if (type != 2 && disc < 0.0) continue;
        float sq = sqrt(max(disc, 0.0));
        float tN = type == 2 ? 0.0 : max(-b - sq, 0.0);
        float tF = type == 2 ? min(segLen, 96.0) : min(-b + sq, segLen);
        vec3 spotDir = normalize(texelFetch(LightTexels, i * 8 + 2).xyz);
        float cosIn = texelFetch(LightTexels, i * 8 + 3).x, cosOut = texelFetch(LightTexels, i * 8 + 3).y;
        if (type == 1 && !clipSpotCone(-pos, rayDir, spotDir, cosOut, tN, tF)) continue;
        if (tF <= tN) continue;

        // per-light step LOD: a light far from the camera (relative to its size) gets fewer steps
        int steps = baseSteps;
        float stepLen = (tF - tN) / float(steps);
        int curve = int(texelFetch(LightTexels, i * 8 + 3).z + 0.5);
        float param = texelFetch(LightTexels, i * 8 + 3).w;
        int sref = int(texelFetch(LightTexels, i * 8 + 5).w);
        vec3 lcol = texelFetch(LightTexels, i * 8 + 1).rgb * texelFetch(LightTexels, i * 8 + 1).w;

        // front-to-back with beer-lambert extinction so the integral saturates instead of blowing
        // up into a giant blob when the camera sits inside the light's sphere
        vec3 acc = vec3(0.0);
        float transmittance = 1.0;
        float stepT = exp(-density * stepLen);
        bool spotShadow = lShadows && ShadowActive != 0 && sref >= 0 && sref < 1000;
        vec4 shadowRay = vec4(0.0), shadowOrigin = vec4(0.0);
        vec2 shadowTile = vec2(0.0);
        if (spotShadow) {
            shadowRay = SpotViewProj[sref] * vec4(rayDir, 0.0);
            shadowOrigin = SpotViewProj[sref][3];
            shadowTile = vec2(float(sref % ShadowGridX), float(sref / ShadowGridX)) * ShadowTileSize;
        }
        for (int s = 0; s < steps; s++) {
            float sampleT = tN + stepLen * (float(s) + dither);
            vec3 p = rayDir * sampleT;
            vec3 toL = pos - p;
            float d = length(toL);
            if (type != 2 && d > range) continue;
            vec3 L = type == 2 ? -spotDir : toL / max(d, 1e-4);
            float a = type == 2 ? 1.0 : falloff(d, range, curve, param);
            if (type == 1) {
                float cd = dot(-L, spotDir);
                a *= smoothstep(0.0, 1.0, clamp((cd - cosOut) / max(cosIn - cosOut, 1e-4), 0.0, 1.0));
            }
            if (a <= 0.0) continue;
            if (spotShadow) a *= spotRayVisibility(shadowRay * sampleT + shadowOrigin, shadowTile);
            else if (lShadows) a *= shadowVisibilityCheap(sref, p, pos, range);
            // energy-conserving single scatter: integral of inscatter * transmittance over the step
            if(a>0.0) {
                float phase = hgPhase(dot(rayDir, L), aniso);
                acc += transmittance * lcol * a * phase * (1.0 - stepT);
            }
            transmittance *= stepT;
            if (transmittance < 0.003) break;
        }
        result += acc * gstr;
    }
    vec3 scatter = max(result * (12.5663706 * VolumetricStrength), vec3(0.0));
    float peak = max(scatter.r, max(scatter.g, scatter.b));
    return scatter * (0.16 * (1.0 - exp(-3.0 * peak)) / max(peak, 1e-6));
}

#include "amnetic:shaders/material/custom_ladder.glsl"

void main() {
    float depth = texture(DepthSampler, vUV).r;

    // late volumetric-only pass over translucents, outputs god-rays additively
    // alpha carries depth for bilateral upscale
    if (VolumetricOnly == 1) {
        if(ScatteringClip==1 && (vUV.x<ScatteringRect.x || vUV.y<ScatteringRect.y
                || vUV.x>ScatteringRect.z || vUV.y>ScatteringRect.w)) {
            FragColor=vec4(0.0,0.0,0.0,depth);return;
        }
        vec3 fp;
        if (skyDepth(depth)) {
            vec3 rd = reconstruct(vUV, 0.5);
            float rl = length(rd);
            if (rl < 1e-5) { FragColor = vec4(0.0, 0.0, 0.0, depth); return; }
            fp = rd * (SKY_RAY_DISTANCE / rl);
        } else {
            fp = reconstruct(vUV, depth);
        }
        FragColor = vec4(volumetric(fp), depth);
        return;
    }

    if (skyDepth(depth)) {
#ifdef AMNETIC_NATIVE
        FragColor = texture(AlbedoSampler, vUV);
#else
        FragColor = vec4(texture(AlbedoSampler, vUV).rgb, 1.0);
#endif
        return;
    }

    vec3 fragPos = reconstruct(vUV, depth);
    vec3 N;
    float rough = 0.7;
    float f0 = 0.04;
    float metallic = 0.0;
    int materialId = 0;
    vec2 lightUV = vec2(0.0);
    if (HasGBuffer == 1) {
        vec4 gn = texture(GNormalSampler, vUV);
        N = (gn.a > 0.5) ? normalize(gn.xyz) : betterNormal(vUV, fragPos);
        vec4 gm = texture(GMaterialSampler, vUV);
        if (gn.a > 0.5 && texture(GAlbedoSampler, vUV).a > 0.5) {
            rough = gm.x;
            metallic = gm.y;
            f0 = mix(0.04, 1.0, metallic);
            materialId = int(gm.z * 255.0 + 0.5);
            float packedLight = gm.w * 255.0;
            float blockIdx = floor(floor(packedLight + 0.5) / 16.0);
            float skyIdx = packedLight - blockIdx * 16.0;
            lightUV = vec2((blockIdx + 0.5) / 16.0, (clamp(skyIdx, 0.0, 15.0) + 0.5) / 16.0);
        }
    } else {
        N = betterNormal(vUV, fragPos);
    }
    // specular AA: widen roughness by the pixel-footprint normal variance so distant and
    // curved surfaces stop sparkling instead of aliasing the highlight
    vec3 nDx = dFdx(N), nDy = dFdy(N);
    float nVar = 0.25 * (dot(nDx, nDx) + dot(nDy, nDy));
    rough = clamp(sqrt(rough * rough + min(2.0 * nVar, 0.18)), 0.0, 1.0);

    vec3 V = normalize(-fragPos); // camera at origin in view space
    vec3 albedo = texture(AlbedoSampler, vUV).rgb;
    // a forward pass can publish its material colour; local lights shade that instead of the lit
    // frame, so a lamp can still light a surface the daylight left at black
    vec4 published = texture(GAlbedoSampler, vUV);
    vec3 material = HasGBuffer == 1 && published.a > 0.5 ? published.rgb : lightMaterial(albedo);

    if (DebugMode != 0) {
        vec3 dbg = vec3(0.0);
        if (DebugMode == 1) dbg = fract(fragPos * 0.0625);
        else if (DebugMode == 2) dbg = N * 0.5 + 0.5;
        else if (DebugMode == 3) dbg = vec3(0.0, 0.12, 0.0);
        FragColor = vec4(dbg, 1.0);
        return;
    }

    vec3 radiance = vec3(0.0);
    vec3 specular = vec3(0.0);
    float sunShadow = 0.0;
    int tileBase=0,tileCount=LightCount;
    if(TileLighting==1) {
        ivec2 cell=clamp(ivec2(vUV*vec2(TileColumns,TileRows)),ivec2(0),ivec2(TileColumns-1,TileRows-1));
        int slice=0;
        if(TileDepthSlices>1) {
            float viewDepth=dot(TileViewDepth,vec4(fragPos,1.0));
            slice=clamp(int(floor(log2(max(viewDepth,1.0))*2.0)),0,TileDepthSlices-1);
        }
        tileBase=texelFetch(TileLights,cell.x+(cell.y+slice*TileRows)*TileColumns).r;
        tileCount=texelFetch(TileLights,tileBase).r;
    }
    for (int entry = 0; entry < tileCount; entry++) {
        int i=TileLighting==1?texelFetch(TileLights,tileBase+1+entry).r:entry;
        // when light volumes are on they shade every non-directional light, so the fullscreen
        // pass only still needs the directional sun (which darkens and cannot be additive)
        if (SkipLocalLights == 1 && int(texelFetch(LightTexels, i * 8 + 2).w + 0.5) != 2) continue;
        radiance += lightContribution(i, fragPos, N, V, rough, f0, specular, sunShadow);
    }

    // scene is already day-lit, darken where the sun is occluded, then local lights and specular on top
    vec4 shade = HasGBuffer == 1 ? texture(GShadeSampler, vUV) : vec4(0.0);
    float sunMul = clamp(1.0 - sunShadow * (shade.b > 0.5 ? shade.g : 1.0), 0.0, 1.0);
    // with light volumes on the local lights were skipped above and the volumes add them after
    vec3 local = surfaceRadiance(material, radiance, specular, metallic);
    vec3 outColor = ShaderPackComposite == 1 ? albedo * sunMul + compositeLight(albedo, local)
            : resolveSurface(albedo, sunMul, material, local);
    outColor = max(outColor, vec3(0.0));
    if (any(isnan(outColor)) || any(isinf(outColor))) outColor = vec3(0.0);

    if (materialId != 0 && ShaderPackComposite == 0) {
        bool handled;
        vec3 custom = shadeCustomMaterial(materialId,
                GBufferSample(albedo, N, fragPos, rough, metallic, outColor,
                        texture(LightmapSampler, lightUV).rgb, LightTime), handled);
        if (handled) outColor = custom;
    }

    float darkPeak=max(outColor.r,max(outColor.g,outColor.b));
    if(max(local.r,max(local.g,local.b))>0.0 && darkPeak<0.14) {
        float noise=(fract(52.9829189*fract(dot(gl_FragCoord.xy,vec2(0.06711056,0.00583715))))-0.5)/255.0;
        vec3 weight=smoothstep(vec3(0.0),vec3(1.0/255.0),outColor)
                * (1.0-smoothstep(0.08,0.14,darkPeak));
        outColor=max(outColor+noise*weight,vec3(0.0));
    }
#ifdef AMNETIC_NATIVE
    FragColor = vec4(outColor, texture(AlbedoSampler, vUV).a);
#else
    FragColor = vec4(outColor, 1.0);
#endif
}

