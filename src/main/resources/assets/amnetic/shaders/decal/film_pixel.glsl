float pxT[36];
float pxS[36];
vec2 pxStart;
float pxRatio;

void pxLoad(vec2 g) {
    pxRatio = Res.x / (Size.x * TexelsPerUnit);
    pxStart = floor((g - 1.5) * pxRatio - 0.5) - 1.0;
    for (int j = 0; j < 6; j++) {
        for (int i = 0; i < 6; i++) {
            ivec2 c = clamp(ivec2(pxStart) + ivec2(i, j), ivec2(0), ivec2(Res) - 1);
            vec4 a = texelFetch(FieldA, c, 0);
            pxT[j * 6 + i] = a.r * a.r * MaxThickness;
            pxS[j * 6 + i] = a.b;
        }
    }
}

vec4 pxWeights(float f) {
    return vec4(f * (-0.5 + f * (1.0 - 0.5 * f)), 1.0 + f * f * (-2.5 + 1.5 * f), f * (0.5 + f * (2.0 - 1.5 * f)), f * f * (-0.5 + 0.5 * f));
}

float pxField(vec2 q, int channel) {
    vec2 x = (q + 0.5) * pxRatio - 0.5;
    vec2 i0 = floor(x);
    vec4 wx = pxWeights(x.x - i0.x);
    vec4 wy = pxWeights(x.y - i0.y);
    ivec2 o = ivec2(i0 - pxStart) - 1;
    float sum = 0.0;
    for (int j = 0; j < 4; j++) {
        float row = 0.0;
        for (int i = 0; i < 4; i++) {
            int k = clamp(o.y + j, 0, 5) * 6 + clamp(o.x + i, 0, 5);
            row += (channel == 0 ? pxT[k] : pxS[k]) * wx[i];
        }
        sum += row * wy[j];
    }
    return sum;
}

bool pxRaw(vec2 q, int channel) {
    return channel == 0 ? pxField(q, 0) > Edge.x : pxField(q, 1) > StainThreshold;
}

bool pxClean(vec2 q, int channel) {
    int n = int(pxRaw(q + vec2(1.0, 0.0), channel)) + int(pxRaw(q - vec2(1.0, 0.0), channel))
          + int(pxRaw(q + vec2(0.0, 1.0), channel)) + int(pxRaw(q - vec2(0.0, 1.0), channel));
    return pxRaw(q, channel) ? n >= 2 : n >= 3;
}

float pxHash(vec2 q) {
    return filmHash(mod(q + OriginTexel, 1024.0) + vec2(0.5, 0.25));
}

float pxLight(float level) {
    float f = floor(level * 15.0 + 0.5) / 15.0;
    return f / (4.0 - 3.0 * f);
}

void filmPixel(out vec4 addColor, out vec4 transmit) {
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(DepthSampler, 0));
    float depth = texture(DepthSampler, uv).r;
    if (IS_SKY(depth)) discard;
    vec3 P = reconstruct(uv, depth);
    vec3 L = (P - Origin) / Size;
    if (any(lessThan(L, vec3(0.0))) || any(greaterThan(L, vec3(1.0)))) discard;

    vec2 g = floor(L.xz * Size.xz * TexelsPerUnit);
    vec2 st = (g + 0.5) / (Size.xz * TexelsPerUnit);

    ivec2 base = ivec2(floor(st * Res - 0.5));
    float nearest = 1e9;
    for (int i = 0; i < 4; i++) {
        ivec2 q = clamp(base + ivec2(i & 1, i >> 1), ivec2(0), ivec2(Res) - 1);
        float e = texelFetch(FieldB, q, 0).r;
        if (e > 0.0) nearest = min(nearest, abs(P.y - (Origin.y + e * Size.y)));
    }
    float dist = length(P);
    float tol = 0.03 + dist * 0.0015;
    if (nearest > tol * 2.0) discard;
    vec3 Ng = betterNormal(uv, P);
    bool degenerate = dot(Ng, -P / max(dist, 1e-4)) > 0.9999;
    if (!degenerate && Ng.y < 0.5) discard;

    pxLoad(g);
    vec4 a = texture(FieldA, st);
    vec4 b = texture(FieldB, st);
    bool aged = b.g > 0.6 || a.g < 0.35;

    vec2 toLight = length(SunDir.xz) > 0.2 ? SunDir.xz : vec2(-1.0, -1.0);
    vec2 lx = vec2(toLight.x > 0.0 ? 1.0 : -1.0, 0.0);
    vec2 ly = vec2(0.0, toLight.y > 0.0 ? 1.0 : -1.0);

    int role = -1;
    bool wet = pxClean(g, 0);
    if (HasPattern == 1) {
        ivec2 tsz = textureSize(PatternWet, 0);
        ivec2 tq = ivec2(mod(g + OriginTexel, vec2(tsz)));
        vec3 col;
        if (wet) {
            bool edge = PixelOutline > 0.5 && (!pxClean(g - lx, 0) || !pxClean(g - ly, 0));
            col = edge ? Palette[4] : (aged ? texelFetch(PatternDry, tq, 0).rgb : texelFetch(PatternWet, tq, 0).rgb);
        } else if (pxClean(g, 1)) {
            bool edge = PixelOutline > 0.5 && (!pxClean(g - lx, 1) || !pxClean(g - ly, 1));
            col = edge ? Palette[6] : texelFetch(PatternDry, tq, 0).rgb;
        } else {
            discard;
        }
        float dayP = clamp(max(SunColor.r, max(SunColor.g, SunColor.b)), 0.06, 1.0);
        float lightP = max(max(pxLight(b.b) * dayP, pxLight(b.a)), 0.05);
        addColor = vec4(col * lightP * Opacity, 1.0);
        transmit = vec4(vec3(1.0 - Opacity), 1.0);
        return;
    }
    if (wet) {
        float h = pxHash(g);
        int shade = h < PixelJitter * 0.3 ? 1 : h > 1.0 - PixelJitter * 0.3 ? 3 : 2;
        if (PixelCore > 0.5 && pxField(g, 0) > Edge.x * 2.6) shade = shade == 1 ? 2 : 3;
        role = aged ? (shade == 3 ? 6 : 5) : shade;
        if (PixelOutline > 0.5 && (!pxClean(g - lx, 0) || !pxClean(g - ly, 0))) role = aged ? 6 : 4;
        if (!aged && PixelHighlights > 0.0 && role != 4) {
            bool nearLit = !pxRaw(g + 2.0 * lx, 0) || !pxRaw(g + 2.0 * ly, 0);
            bool interior = pxClean(g + lx, 0) && pxClean(g + ly, 0);
            if (nearLit && interior && pxHash(g + 31.0) < PixelHighlights * 0.35) {
                bool lit = b.b > 0.8 && SunDir.y > 0.1 || b.a > 0.5;
                for (int i = 0; i < 4; i++) {
                    if (i >= LightCount) break;
                    vec3 Lv = LightPos[i].xyz - P;
                    float d = length(Lv);
                    if (d > LightPos[i].w) continue;
                    if (LightDir[i].w > 0.5 && dot(-Lv / d, LightDir[i].xyz) < LightCol[i].w) continue;
                    lit = true;
                }
                if (lit) role = 0;
            }
        }
    } else if (PixelOutline > 1.5 && pxField(g, 0) > Edge.x * 0.15
               && (pxClean(g + vec2(1.0, 0.0), 0) || pxClean(g - vec2(1.0, 0.0), 0) || pxClean(g + vec2(0.0, 1.0), 0) || pxClean(g - vec2(0.0, 1.0), 0))) {
        role = 4;
    } else if (pxClean(g, 1)) {
        role = pxHash(g + 7.0) < PixelJitter * 0.3 ? 6 : 7;
        if (PixelOutline > 0.5 && (!pxClean(g - lx, 1) || !pxClean(g - ly, 1))) role = 6;
    }
    if (role < 0) discard;

    float day = clamp(max(SunColor.r, max(SunColor.g, SunColor.b)), 0.06, 1.0);
    float light = max(max(pxLight(b.b) * day, pxLight(b.a)), 0.05);
    vec3 col = Palette[role] * (role == 0 ? max(light, 0.8) : light);
    addColor = vec4(col * Opacity, 1.0);
    transmit = vec4(vec3(1.0 - Opacity), 1.0);
}
