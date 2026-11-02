
uniform sampler2D SpotShadowAtlas;
uniform sampler2D SpotShadowColor; // nearest-occluder tint for colored shadows
#ifdef AMNETIC_NATIVE
uniform sampler2D PointShadowArray;
uniform int PointShadowRows;
float amneticPointDepth(vec3 d, int slot) {
    vec3 a = abs(d); vec2 uv; int face;
    if (a.x >= a.y && a.x >= a.z) {
        face = d.x >= 0.0 ? 0 : 1;
        uv = vec2(d.x >= 0.0 ? -d.z : d.z, -d.y) / max(a.x, 1e-6);
    } else if (a.y >= a.z) {
        face = d.y >= 0.0 ? 2 : 3;
        uv = vec2(d.x, d.y >= 0.0 ? d.z : -d.z) / max(a.y, 1e-6);
    } else {
        face = d.z >= 0.0 ? 4 : 5;
        uv = vec2(d.z >= 0.0 ? d.x : -d.x, -d.y) / max(a.z, 1e-6);
    }
    uv = uv * 0.5 + 0.5;
    vec2 size = vec2(textureSize(PointShadowArray, 0));
    vec2 grid = vec2(6.0, float(max(PointShadowRows, 1)));
    vec2 tileSize = size / grid;
    uv = clamp(uv, 0.5 / tileSize, 1.0 - 0.5 / tileSize);
    return texture(PointShadowArray, (uv + vec2(float(face), float(slot))) / grid).r;
}
#define AMNETIC_POINT_DEPTH(d, slot) amneticPointDepth(d, slot)
#define AMNETIC_POINT_TEXEL (2.0 / max(float(textureSize(PointShadowArray, 0).x) / 6.0, 1.0))
#else
uniform samplerCubeArray PointShadowArray;
#define AMNETIC_POINT_DEPTH(d, slot) texture(PointShadowArray, vec4(d, float(slot))).r
#define AMNETIC_POINT_TEXEL 0.0025
#endif
uniform mat4 SpotViewProj[16]; // camera-relative spot view-projection per atlas tile
uniform float ShadowAtlasSize; // atlas edge, px
uniform float ShadowTileSize; // spot tile edge, px
uniform int ShadowGridX; // tiles per atlas row
uniform float ShadowBias;
uniform float ShadowNormalBias; // world-space push along N, in blocks
uniform float ShadowSoftnessTexels;
uniform float ShadowFadeStart; // camera distance where shadows begin fading to lit
uniform float ShadowFadeEnd; // camera distance where shadows are fully gone
uniform int ShadowPcss; // 1 = contact-hardening
uniform float ShadowLightSize;
uniform int ShadowActive;

uniform sampler2DArrayShadow SunShadowMap; // depth per cascade, tightest first
uniform mat4 SunViewProj[4]; // camera-relative ortho view-projection per cascade
uniform float SunCascadeRadius[4]; // cascade world radius; texel = 2r / SunShadowRes
uniform int SunCascadeCount;
uniform float SunShadowRes;
uniform float SunShadowDistance; // far edge of the loosest cascade, shadows fade out toward it
uniform int SunShadowActive;

const vec2 AMNETIC_POISSON[16] = vec2[16](
    vec2(-0.94201624, -0.39906216), vec2(0.94558609, -0.76890725),
    vec2(-0.09418410, -0.92938870), vec2(0.34495938, 0.29387760),
    vec2(-0.91588581, 0.45771432), vec2(-0.81544232, -0.87912464),
    vec2(-0.38277543, 0.27676845), vec2(0.97484398, 0.75648379),
    vec2(0.44323325, -0.97511554), vec2(0.53742981, -0.47373420),
    vec2(-0.26496911, -0.41893023), vec2(0.79197514, 0.19090188),
    vec2(-0.24188840, 0.99706507), vec2(-0.81409955, 0.91437590),
    vec2(0.19984126, 0.78641367), vec2(0.14383161, -0.14100790)
);

const vec2 AMNETIC_VOGEL[8] = vec2[8](
    vec2(0.250000, 0.000000), vec2(-0.319290, 0.292496),
    vec2(0.048872, -0.556877), vec2(0.402444, 0.524918),
    vec2(-0.738535, -0.130636), vec2(0.699605, -0.445031),
    vec2(-0.234004, 0.870484), vec2(-0.446271, -0.859268)
);

float amnetic_ign() {
    return fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
}

float amnetic_spotCompare(vec2 tileOrigin, vec2 tilePixel, float receiver) {
    vec2 p=tilePixel-0.5;
    vec2 cell=floor(p), blend=fract(p);
    vec2 hi=vec2(ShadowTileSize-1.0);
    ivec2 a=ivec2(tileOrigin+clamp(cell,vec2(0.0),hi));
    ivec2 b=ivec2(tileOrigin+clamp(cell+vec2(1,0),vec2(0.0),hi));
    ivec2 c=ivec2(tileOrigin+clamp(cell+vec2(0,1),vec2(0.0),hi));
    ivec2 d=ivec2(tileOrigin+clamp(cell+vec2(1,1),vec2(0.0),hi));
    float va=step(receiver,texelFetch(SpotShadowAtlas,a,0).r);
    float vb=step(receiver,texelFetch(SpotShadowAtlas,b,0).r);
    float vc=step(receiver,texelFetch(SpotShadowAtlas,c,0).r);
    float vd=step(receiver,texelFetch(SpotShadowAtlas,d,0).r);
    return mix(mix(va,vb,blend.x),mix(vc,vd,blend.x),blend.y);
}

vec3 amnetic_sampleSpot(int tile, vec3 fragPos, vec3 N) {
    vec4 c = SpotViewProj[tile] * vec4(fragPos + N * ShadowNormalBias, 1.0);
    if (c.w <= 0.0) return vec3(1.0);
    vec3 ndc = c.xyz / c.w;
    if (abs(ndc.x) > 1.0 || abs(ndc.y) > 1.0 || ndc.z > 1.0) return vec3(1.0);

    vec2 uv = ndc.xy * 0.5 + 0.5;
    float fragDepth = ndc.z * 0.5 + 0.5;
    float spotBias = ShadowBias * min(1.0, 5.0 / max(c.w * c.w, 1e-4));

    float ox = float(tile % ShadowGridX) * ShadowTileSize;
    float oy = float(tile / ShadowGridX) * ShadowTileSize;
    float texel = 1.0 / ShadowAtlasSize;

    float radius = max(ShadowSoftnessTexels, 0.0) * (1.0 + fragDepth * 1.5);

    if (ShadowPcss == 1) {
        float blockerSum = 0.0; int blockers = 0;
        for (int i = 0; i < 16; i++) {
            vec2 off = AMNETIC_POISSON[i] * ShadowLightSize;
            vec2 tilePx = clamp(uv * ShadowTileSize + off, vec2(0.5), vec2(ShadowTileSize - 0.5));
            float d = texture(SpotShadowAtlas, (vec2(ox, oy) + tilePx) * texel).r;
            if (d < fragDepth - spotBias) { blockerSum += d; blockers++; }
        }
        if (blockers == 0) return vec3(1.0); // nothing in front, fully lit
        float avgBlocker = blockerSum / float(blockers);
        float penumbra = (fragDepth - avgBlocker) / max(avgBlocker, 1e-4);
        radius = clamp(penumbra * ShadowLightSize * 8.0, 0.5, ShadowLightSize * 3.0);
    }

    mat2 rot = mat2(0.8660254, -0.5, 0.5, 0.8660254);

    float lit = 0.0;
    for (int i = 0; i < 16; i++) {
        vec2 off = rot * AMNETIC_POISSON[i] * radius;
        vec2 tilePx = clamp(uv * ShadowTileSize + off, vec2(0.5), vec2(ShadowTileSize - 0.5));
        lit += amnetic_spotCompare(vec2(ox,oy),tilePx,fragDepth-spotBias);
    }
    float v = lit / 16.0;
    vec2 centerPx = clamp(uv * ShadowTileSize, vec2(0.5), vec2(ShadowTileSize - 0.5));
    vec3 col = texture(SpotShadowColor, (vec2(ox, oy) + centerPx) * texel).rgb;
    return mix(col, vec3(1.0), v);
}

vec3 amnetic_samplePoint(int slot, vec3 fragPos, vec3 lightPosRel, float range) {
    vec3 d = fragPos - lightPosRel; // world-space light
    float ze = max(max(abs(d.x), abs(d.y)), abs(d.z));
    float n = 0.05;
    float f = max(range, n + 0.1);
    float ndcZ = (f + n) / (f - n) - (2.0 * f * n) / ((f - n) * max(ze, 1e-4));
    float fragDepth = ndcZ * 0.5 + 0.5;
    float pointBias = ShadowBias * min(1.0, 100.0 * f * n / ((f - n) * max(ze * ze, 1e-4)));

    vec3 dir = normalize(d);
    vec3 up = abs(dir.y) > 0.99 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
    vec3 t = normalize(cross(up, dir));
    vec3 b = cross(dir, t);

    float radius = max(ShadowSoftnessTexels, 0.0) * AMNETIC_POINT_TEXEL * ze;

    float a = amnetic_ign() * 6.2831853;
    float ca = cos(a), sa = sin(a);

    float lit = 0.0;
    for (int i = 0; i < 16; i++) {
        vec2 p = AMNETIC_POISSON[i];
        vec2 r2 = vec2(p.x * ca - p.y * sa, p.x * sa + p.y * ca);
        vec3 sampleDir = d + (t * r2.x + b * r2.y) * radius;
        float stored = AMNETIC_POINT_DEPTH(sampleDir, slot);
        lit += (fragDepth - pointBias <= stored) ? 1.0 : 0.0;
    }
    return vec3(lit / 16.0); // point shadows are grayscale, no color map for the cube array yet
}

// cascaded sun shadows: first-fit cascade selection (tightest wins), hardware-filtered PCF
float amnetic_sampleSun(vec3 fragPos, vec3 N) {
    if (SunShadowActive == 0) return 1.0;
    for (int c = 0; c < SunCascadeCount; c++) {
        float worldTexel = 2.0 * SunCascadeRadius[c] / SunShadowRes;
        vec3 biased = fragPos + N * max(ShadowNormalBias, worldTexel * 1.75);
        vec3 ndc = (SunViewProj[c] * vec4(biased, 1.0)).xyz; // ortho: w == 1
        // keep a border so the PCF disk never reads outside this cascade; fall through to the next one
        if (abs(ndc.x) > 0.97 || abs(ndc.y) > 0.97 || ndc.z < -1.0 || ndc.z > 1.0) continue;

        vec2 uv = ndc.xy * 0.5 + 0.5;
        float ref = ndc.z * 0.5 + 0.5 - ShadowBias * (1.0 + float(c)); // looser cascades span more depth per unit
        float radius = max(ShadowSoftnessTexels, 0.0);
        if (radius < 1.0) return texture(SunShadowMap, vec4(uv, float(c), ref));

        float a = amnetic_ign() * 6.2831853;
        float ca = cos(a), sa = sin(a);
        mat2 rot = mat2(ca, -sa, sa, ca) * (radius / SunShadowRes);

        float lit = 0.0;
        for (int i = 0; i < 8; i++) {
            lit += texture(SunShadowMap, vec4(uv + rot * AMNETIC_VOGEL[i], float(c), ref));
        }
        return lit / 8.0;
    }
    return 1.0; // beyond the loosest cascade
}

vec3 shadowVisibility(int shadowRef, vec3 fragPos, vec3 N, vec3 lightPosRel, float range) {
    if (ShadowActive == 0 || shadowRef < 0) return vec3(1.0);
    if (shadowRef >= 2000) return vec3(amnetic_sampleSun(fragPos, N));
    if (shadowRef >= 1000) return amnetic_samplePoint(shadowRef - 1000, fragPos + N * ShadowNormalBias, lightPosRel, range);
    return amnetic_sampleSpot(shadowRef, fragPos, N);
}

float shadowVisibilityCheap(int shadowRef, vec3 fragPos, vec3 lightPosRel, float range) {
    if (ShadowActive == 0 || shadowRef < 0) return 1.0;
    if (shadowRef >= 2000) {
        if (SunShadowActive == 0) return 1.0;
        for (int c = 0; c < SunCascadeCount; c++) {
            vec3 ndc = (SunViewProj[c] * vec4(fragPos, 1.0)).xyz;
            if (abs(ndc.x) > 0.97 || abs(ndc.y) > 0.97 || ndc.z < -1.0 || ndc.z > 1.0) continue;
            float fd = ndc.z * 0.5 + 0.5 - ShadowBias * (1.0 + float(c));
            return texture(SunShadowMap, vec4(ndc.xy * 0.5 + 0.5, float(c), fd));
        }
        return 1.0;
    }
    if (shadowRef >= 1000) {
        int slot = shadowRef - 1000;
        vec3 d = fragPos - lightPosRel;
        float ze = max(max(abs(d.x), abs(d.y)), abs(d.z));
        float n = 0.05, f = max(range, n + 0.1);
        float fd = ((f + n) / (f - n) - (2.0 * f * n) / ((f - n) * max(ze, 1e-4))) * 0.5 + 0.5;
        float pointBias = ShadowBias * min(1.0, 100.0 * f * n / ((f - n) * max(ze * ze, 1e-4)));
        return (fd - pointBias <= AMNETIC_POINT_DEPTH(d, slot)) ? 1.0 : 0.0;
    }
    vec4 c = SpotViewProj[shadowRef] * vec4(fragPos, 1.0);
    if (c.w <= 0.0) return 1.0;
    vec3 ndc = c.xyz / c.w;
    if (abs(ndc.x) > 1.0 || abs(ndc.y) > 1.0 || ndc.z > 1.0) return 1.0;
    vec2 uv = ndc.xy * 0.5 + 0.5;
    float fd = ndc.z * 0.5 + 0.5;
    float ox = float(shadowRef % ShadowGridX) * ShadowTileSize;
    float oy = float(shadowRef / ShadowGridX) * ShadowTileSize;
    float stored = texture(SpotShadowAtlas, (vec2(ox, oy) + clamp(uv * ShadowTileSize, vec2(0.5), vec2(ShadowTileSize - 0.5))) / ShadowAtlasSize).r;
    float spotBias = ShadowBias * min(1.0, 5.0 / max(c.w * c.w, 1e-4));
    return (fd - spotBias <= stored) ? 1.0 : 0.0;
}
