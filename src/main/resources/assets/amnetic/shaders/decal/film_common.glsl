// shared by the film colour and gbuffer passes

uniform sampler2D DepthSampler;
uniform sampler2D FieldA; // thickness (sqrt encoded), wetness, stain, flow
uniform sampler2D FieldB; // floor height in the box, age, sky light, block light
uniform mat4 InvViewProj;
uniform int ZeroToOne;
uniform vec3 Origin; // camera relative box corner
uniform vec3 Size;
uniform vec2 Res;
uniform float Opacity;
uniform float MaxThickness;
uniform vec3 Absorb;
uniform vec3 AbsorbAged;
uniform vec3 StainTint;
uniform vec3 Scatter;
uniform vec2 Edge; // thickness where the liquid edge sits, softness
uniform vec2 EdgeNoise; // amount, features per world unit
uniform float StainThreshold;
uniform float Ring;
uniform vec2 Normals; // slope gain, extra gain at the rim
uniform vec2 Flow; // ripple speed, ripple strength
uniform float Parallax;
uniform float Specular;
uniform vec2 Rough; // wet, dry
uniform float Reflectance;
uniform vec3 SunDir;
uniform vec3 SunColor;
uniform vec3 SkyColor;
uniform vec3 BlockColor;
uniform float Time;
// pixel art mode: world snapped texels, a fixed palette, crisp outlines
uniform int PixelArt;
uniform float TexelsPerUnit;
// roles: 0 highlight, 1 light body, 2 body, 3 dark body, 4 edge, 5 aged body, 6 aged dark and dried edge, 7 dried stain
uniform vec3 Palette[8];
uniform vec2 OriginTexel;
uniform float PixelJitter;
uniform float PixelOutline;
uniform float PixelCore;
uniform float PixelHighlights;
// optional block style textures for the pixel film, 1 texel per world texel
uniform sampler2D PatternWet;
uniform sampler2D PatternDry;
uniform int HasPattern;
// up to 4 nearby amnetic lights: xyz camera relative, w range / rgb * intensity, w cos outer / spot axis, w 1 for spot
uniform vec4 LightPos[4];
uniform vec4 LightCol[4];
uniform vec4 LightDir[4];
uniform int LightCount;
// rain falling on the liquid: amount 0..1, ripple cells per world unit, ripple slope
uniform vec3 RainRipples;

#include "amnetic:shaders/common/screen.glsl"

float filmHash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float filmNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(filmHash(i), filmHash(i + vec2(1.0, 0.0)), u.x),
               mix(filmHash(i + vec2(0.0, 1.0)), filmHash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float filmFbm(vec2 p) {
    return 0.55 * filmNoise(p) + 0.3 * filmNoise(p * 2.13 + 7.1) + 0.15 * filmNoise(p * 4.37 + 3.3);
}

float filmThickness(vec2 st) {
    float e = texture(FieldA, st).r;
    return e * e * MaxThickness;
}

// expanding rings from drops hitting the liquid, 3 offset layers of random cells, one drop per cell
// gives the slope to bend the normal with, z is a tiny splash flash right where a drop just landed
vec3 filmRain(vec2 w, float amount) {
    vec3 acc = vec3(0.0);
    for (int l = 0; l < 3; l++) {
        float fl = float(l);
        vec2 p = w * RainRipples.y * (1.0 + fl * 0.37) + vec2(fl * 17.3, fl * 31.7);
        vec2 id = floor(p);
        vec2 f = fract(p);
        float h0 = filmHash(id + fl * 3.1);
        // more cells get a drop the harder it rains
        if (h0 > amount * 0.85 + 0.05) continue;
        float h1 = filmHash(id + vec2(5.2, 1.3) + fl);
        float h2 = filmHash(id + vec2(2.7, 8.9) + fl);
        // the ring stays inside its cell so neighbours never need checking
        vec2 c = vec2(0.3) + 0.4 * vec2(h1, h2);
        float life = fract(Time * (0.9 + 0.35 * fl + 0.3 * h1) + h0 * 7.0);
        vec2 d = f - c;
        float r = length(d);
        float radius = life * 0.3;
        float band = (r - radius) / 0.07;
        if (abs(band) < 1.0) {
            float fade = (1.0 - life) * (1.0 - life);
            float g = sin(band * 6.2832) * (1.0 - abs(band)) * fade;
            acc.xy += d / max(r, 1e-4) * g;
        }
        if (life < 0.1) acc.z += (1.0 - life * 10.0) * (1.0 - smoothstep(0.0, 0.05, r));
    }
    return acc;
}

struct FilmSample {
    vec3 P;
    vec3 N;
    vec3 V;
    float cov; // liquid coverage
    float t; // liquid thickness, world units
    float wet;
    float stain; // dried residue after coverage and ring
    float age;
    float flow;
    float sky;
    float block;
    float mask; // on the right floor and facing up
    float splash; // drop impacts, 0..1
    float dist; // distance from the camera
};

bool filmSample(out FilmSample s) {
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(DepthSampler, 0));
    float depth = texture(DepthSampler, uv).r;
    if (IS_SKY(depth)) return false;
    vec3 P = reconstruct(uv, depth);
    vec3 L = (P - Origin) / Size;
    if (any(lessThan(L, vec3(0.0))) || any(greaterThan(L, vec3(1.0)))) return false;
    vec2 st = L.xz;

    // each texel knows which floor it belongs to, anything off that height (a step, a wall, a leg) stays clean
    // floors are read exact from the 4 nearest texels (0 means unset), the closest one wins so edges stay soft
    ivec2 base = ivec2(floor(st * Res - 0.5));
    float nearest = 1e9;
    for (int i = 0; i < 4; i++) {
        ivec2 q = clamp(base + ivec2(i & 1, i >> 1), ivec2(0), ivec2(Res) - 1);
        float e = texelFetch(FieldB, q, 0).r;
        if (e > 0.0) nearest = min(nearest, abs(P.y - (Origin.y + e * Size.y)));
    }
    if (nearest > 1e8) return false;
    // depth gets coarser with distance and at grazing angles, so the height tolerance grows with them
    float dist = length(P);
    float tol = 0.03 + dist * 0.0015;
    float onFloor = 1.0 - smoothstep(tol, tol * 2.0, nearest);
    // the depth normal goes degenerate on far floors seen edge on (it falls back to facing the camera),
    // there the floor height test alone decides instead of dropping the film
    vec3 Ng = betterNormal(uv, P);
    bool degenerate = dot(Ng, -P / max(dist, 1e-4)) > 0.9999;
    float facing = degenerate ? 1.0 : smoothstep(0.35, 0.7, Ng.y);
    if (degenerate) Ng = vec3(0.0, 1.0, 0.0);
    float mask = onFloor * facing * Opacity;
    if (mask <= 0.001) return false;

    vec3 V = normalize(-P);
    // the liquid top sits above the floor, the view ray meets it a little earlier
    if (Parallax > 0.0) {
        float t0 = filmThickness(st);
        st += V.xz * (t0 * Parallax / max(0.25, V.y)) / Size.xz;
    }
    vec4 a = texture(FieldA, st);
    vec4 b = texture(FieldB, st);
    float t = a.r * a.r * MaxThickness;
    vec2 texel = 1.0 / Res;

    // edges come from a wider tent filter, plain bilinear on a cell field leaves stair steps along the outline
    vec4 wide = 0.25 * (texture(FieldA, st + vec2(-0.5, -0.5) * texel) + texture(FieldA, st + vec2(0.5, -0.5) * texel)
                      + texture(FieldA, st + vec2(-0.5, 0.5) * texel) + texture(FieldA, st + vec2(0.5, 0.5) * texel));
    float tw = wide.r * wide.r * MaxThickness;

    // the outline is broken up by noise anchored to the film, so it doesn't swim when the camera moves
    vec2 w = st * Size.xz;
    float n = filmFbm(w * EdgeNoise.y);
    float threshold = Edge.x * (1.0 + EdgeNoise.x * (n - 0.5) * 2.0);
    float cov = smoothstep(threshold - Edge.y, threshold + Edge.y, tw);

    // dried residue darkens most where it changes fastest, the coffee ring
    float sx = texture(FieldA, st + vec2(texel.x, 0.0)).b - texture(FieldA, st - vec2(texel.x, 0.0)).b;
    float sz = texture(FieldA, st + vec2(0.0, texel.y)).b - texture(FieldA, st - vec2(0.0, texel.y)).b;
    float ringBand = clamp(length(vec2(sx, sz)) * 2.5, 0.0, 1.0);
    float stainCov = smoothstep(StainThreshold * 0.5, StainThreshold * 1.5, wide.b + (n - 0.5) * 0.12);
    float stain = clamp(a.b * (1.0 + Ring * ringBand), 0.0, 1.0) * stainCov;

    // normals from the thickness field, steeper at the rim so the pool edge rounds off like a meniscus
    vec2 wt = Size.xz / Res;
    float tx = filmThickness(st + vec2(texel.x, 0.0)) - filmThickness(st - vec2(texel.x, 0.0));
    float tz = filmThickness(st + vec2(0.0, texel.y)) - filmThickness(st - vec2(0.0, texel.y));
    float rim = 1.0 + Normals.y * (1.0 - smoothstep(Edge.x, Edge.x * 4.0, t));
    vec2 grad = vec2(tx, tz) / (2.0 * wt) * Normals.x * rim;
    // slow ripples where it's still running
    if (a.a > 0.0) {
        vec2 rp = w * 7.0;
        float tm = Time * Flow.x;
        float r1 = filmNoise(rp + vec2(tm, tm * 0.7));
        float r2 = filmNoise(rp + vec2(1.7, 4.2) - vec2(tm * 0.6, tm));
        grad += (vec2(r1, r2) - 0.5) * Flow.y * a.a;
    }
    // rain rings, faded out with distance before they turn to aliasing noise
    float splash = 0.0;
    // only under open sky, sky light drops off as soon as something covers the spot
    float rainFade = RainRipples.x * (1.0 - smoothstep(16.0, 36.0, dist)) * smoothstep(0.86, 0.97, b.b);
    if (rainFade > 0.001 && cov > 0.01) {
        vec3 rr = filmRain(w, RainRipples.x);
        grad += rr.xy * RainRipples.z * rainFade;
        splash = clamp(rr.z, 0.0, 1.0) * rainFade;
    }
    vec3 N = normalize(Ng + vec3(-grad.x, 0.0, -grad.y) * cov);

    s.P = P;
    s.N = N;
    s.V = V;
    s.cov = cov;
    s.t = t;
    s.wet = a.g;
    s.stain = stain;
    s.age = b.g;
    s.flow = a.a;
    s.sky = b.b;
    s.block = b.a;
    s.mask = mask;
    s.splash = splash * cov;
    s.dist = dist;
    return true;
}
