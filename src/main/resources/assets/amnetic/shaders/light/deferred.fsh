#version 430 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D AlbedoSampler;
uniform sampler2D DepthSampler;

uniform mat4 InvViewProj;
uniform int ZeroToOne;
uniform int LightCount;
uniform int VolumetricSteps;
uniform float VolumetricStrength;
uniform int DebugMode;

// 6 vec4 per light (std430): see LightBuffer.
layout(std430, binding = 0) readonly buffer LightData { vec4 lights[]; };

vec3 reconstruct(vec2 uv, float depth) {
    float z = (ZeroToOne == 1) ? depth : depth * 2.0 - 1.0;
    vec4 clip = vec4(uv * 2.0 - 1.0, z, 1.0);
    vec4 p = InvViewProj * clip;
    return p.xyz / p.w;   // camera-relative
}

// "Better normal": pick the closer horizontal/vertical depth neighbour so silhouettes don't smear.
vec3 betterNormal(vec2 uv, vec3 P) {
    vec2 ts = 1.0 / vec2(textureSize(DepthSampler, 0));
    float c  = texture(DepthSampler, uv).r;
    float dl = texture(DepthSampler, uv - vec2(ts.x, 0.0)).r;
    float dr = texture(DepthSampler, uv + vec2(ts.x, 0.0)).r;
    float dd = texture(DepthSampler, uv - vec2(0.0, ts.y)).r;
    float du = texture(DepthSampler, uv + vec2(0.0, ts.y)).r;
    vec3 Px = (abs(dl - c) < abs(dr - c)) ? (P - reconstruct(uv - vec2(ts.x, 0.0), dl))
                                          : (reconstruct(uv + vec2(ts.x, 0.0), dr) - P);
    vec3 Py = (abs(dd - c) < abs(du - c)) ? (P - reconstruct(uv - vec2(0.0, ts.y), dd))
                                          : (reconstruct(uv + vec2(0.0, ts.y), du) - P);
    vec3 n = cross(Px, Py);
    float l = length(n);
    n = (l > 1e-8) ? n / l : vec3(0.0, 1.0, 0.0);
    if (dot(n, P) > 0.0) n = -n;   // face the camera (origin in camera-relative space)
    return n;
}

float falloff(float dist, float range, int curve, float param) {
    float t = clamp(dist / max(range, 1e-4), 0.0, 1.0);
    if (curve == 0) return 1.0 - smoothstep(0.0, 1.0, t);              // SMOOTH
    if (curve == 1) return 1.0 - t;                                   // LINEAR
    if (curve == 2) {                                                 // INVERSE_SQUARE (windowed)
        float d2 = dist * dist;
        float win = clamp(1.0 - (d2 * d2) / (range * range * range * range), 0.0, 1.0);
        return (win * win) / (d2 + 1.0);
    }
    return pow(1.0 - t, param);                                       // EXPONENT
}

vec3 closestSegment(vec3 p, vec3 a, vec3 b) {
    vec3 ab = b - a;
    float t = clamp(dot(p - a, ab) / max(dot(ab, ab), 1e-4), 0.0, 1.0);
    return a + ab * t;
}

// Surface radiance from light i (already multiplied by N·L and falloff; caller multiplies albedo).
vec3 lightContribution(int i, vec3 fragPos, vec3 N) {
    vec3 pos     = lights[i * 6 + 0].xyz;
    float range  = lights[i * 6 + 0].w;
    vec3 color   = lights[i * 6 + 1].rgb;
    float intens = lights[i * 6 + 1].w;
    vec3 dir     = lights[i * 6 + 2].xyz;
    int type     = int(lights[i * 6 + 2].w + 0.5);
    float cosIn  = lights[i * 6 + 3].x;
    float cosOut = lights[i * 6 + 3].y;
    int curve    = int(lights[i * 6 + 3].z + 0.5);
    float param  = lights[i * 6 + 3].w;
    float areaW  = lights[i * 6 + 4].x;
    float areaH  = lights[i * 6 + 4].y;
    float tubeL  = lights[i * 6 + 4].z;
    vec3 tangent = lights[i * 6 + 5].xyz;

    vec3 L;
    float atten;
    if (type == 2) {                       // DIRECTIONAL
        L = -normalize(dir);
        atten = 1.0;
    } else {
        vec3 src = pos;                    // representative point for area lights
        if (type == 3) {                   // AREA_RECT
            vec3 u = normalize(tangent);
            vec3 v = normalize(cross(dir, u));
            vec3 d = fragPos - pos;
            src = pos + u * clamp(dot(d, u), -areaW, areaW) + v * clamp(dot(d, v), -areaH, areaH);
        } else if (type == 4) {            // AREA_DISC
            vec3 n = normalize(dir);
            vec3 d = fragPos - pos;
            vec3 proj = d - n * dot(d, n);
            float pl = length(proj);
            if (pl > areaW) proj *= areaW / pl;
            src = pos + proj;
        } else if (type == 5) {            // TUBE
            vec3 u = normalize(tangent);
            src = closestSegment(fragPos, pos - u * tubeL * 0.5, pos + u * tubeL * 0.5);
        }
        vec3 toL = src - fragPos;
        float dist = length(toL);
        if (dist > range) return vec3(0.0);
        L = toL / max(dist, 1e-4);
        atten = falloff(dist, range, curve, param);
        if (type == 1) {                   // SPOT cone (measured from the light centre)
            vec3 Lc = normalize(pos - fragPos);
            float cd = dot(-Lc, normalize(dir));
            atten *= clamp((cd - cosOut) / max(cosIn - cosOut, 1e-4), 0.0, 1.0);
        }
    }
    float ndotl = max(dot(N, L), 0.0);
    if (ndotl <= 0.0 || atten <= 0.0) return vec3(0.0);
    return color * intens * ndotl * atten;
}

// In-scattering toward the lights along the view ray (god rays / volumetric beams).
vec3 volumetric(vec3 fragPos) {
    if (VolumetricSteps <= 0) return vec3(0.0);
    float segLen = length(fragPos);
    if (segLen < 1e-3) return vec3(0.0);
    vec3 stepv = fragPos / float(VolumetricSteps);
    float stepLen = segLen / float(VolumetricSteps);
    float jitter = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    vec3 p = stepv * jitter;
    vec3 scatter = vec3(0.0);
    for (int s = 0; s < VolumetricSteps; s++) {
        p += stepv;
        for (int i = 0; i < LightCount; i++) {
            int type = int(lights[i * 6 + 2].w + 0.5);
            if (type == 2) continue;       // directional has no position to scatter from
            vec3 pos = lights[i * 6 + 0].xyz;
            float range = lights[i * 6 + 0].w;
            vec3 toL = pos - p;
            float d = length(toL);
            if (d > range) continue;
            int curve = int(lights[i * 6 + 3].z + 0.5);
            float a = falloff(d, range, curve, lights[i * 6 + 3].w);
            if (type == 1) {
                vec3 Lc = normalize(pos - p);
                float cd = dot(-Lc, normalize(lights[i * 6 + 2].xyz));
                a *= clamp((cd - lights[i * 6 + 3].y) / max(lights[i * 6 + 3].x - lights[i * 6 + 3].y, 1e-4), 0.0, 1.0);
            }
            scatter += lights[i * 6 + 1].rgb * lights[i * 6 + 1].w * a;
        }
    }
    // average over steps (so the result doesn't scale with step count / ray length), then clamp so a
    // light near the camera can't blow the frame to white.
    return clamp(scatter / float(VolumetricSteps) * VolumetricStrength, vec3(0.0), vec3(1.0));
}

void main() {
    float depth = texture(DepthSampler, vUV).r;
    if (depth >= 1.0) { FragColor = vec4(0.0); return; }   // sky: no surface to light

    vec3 fragPos = reconstruct(vUV, depth);
    vec3 N = betterNormal(vUV, fragPos);
    vec3 albedo = texture(AlbedoSampler, vUV).rgb;

    if (DebugMode != 0) {
        vec3 dbg = vec3(0.0);
        if (DebugMode == 1)      dbg = fract(fragPos * 0.0625);
        else if (DebugMode == 2) dbg = N * 0.5 + 0.5;
        else if (DebugMode == 3) dbg = vec3(0.0, 0.12, 0.0);
        FragColor = vec4(dbg, 1.0);
        return;
    }

    vec3 radiance = vec3(0.0);
    for (int i = 0; i < LightCount; i++) radiance += lightContribution(i, fragPos, N);

    vec3 scatter = volumetric(fragPos);   // in-air, not modulated by albedo

    vec3 outColor = albedo * radiance + scatter;
    outColor = max(outColor, vec3(0.0));
    if (any(isnan(outColor)) || any(isinf(outColor))) outColor = vec3(0.0);

    float r0 = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    float r1 = fract(sin(dot(gl_FragCoord.xy, vec2(93.9898, 67.345))) * 24634.6345);
    outColor += vec3((r0 + r1 - 1.0) / 255.0);   // triangular dither vs 8-bit banding

    FragColor = vec4(outColor, 1.0);
}
