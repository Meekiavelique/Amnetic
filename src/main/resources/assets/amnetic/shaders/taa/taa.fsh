#version 430 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D ColorSampler; // current frame (rendered with sub-pixel jitter)
uniform sampler2D History; // previous resolved frame
uniform sampler2D DepthSampler;
uniform mat4 PrevViewProj; // previous frame view-proj, unjittered
uniform mat4 InvViewProj; // current frame inverse view-proj, unjittered
uniform vec3 EyeDelta; // current eye - previous eye
uniform int ZeroToOne;
uniform float Feedback;
uniform float ClipSigma; // neighbourhood clip width, in standard deviations

// local copy of the camera-relative position reconstruct, screen.glsl also drags in
// depth-derivative normal helpers this pass doesn't want
vec3 reconstruct(vec2 uv, float depth) {
    float z = (ZeroToOne == 1) ? depth : depth * 2.0 - 1.0;
    vec4 clip = vec4(uv * 2.0 - 1.0, z, 1.0);
    vec4 p = InvViewProj * clip;
    return p.xyz / p.w;
}

vec3 rgb2ycocg(vec3 c) {
    return vec3(c.r * 0.25 + c.g * 0.5 + c.b * 0.25,
                c.r * 0.5 - c.b * 0.5,
                -c.r * 0.25 + c.g * 0.5 - c.b * 0.25);
}

vec3 ycocg2rgb(vec3 c) {
    return vec3(c.x + c.y - c.z, c.x + c.z, c.x - c.y - c.z);
}

// clip (not clamp) the history colour toward the neighbourhood box centre, clipping keeps
// the hue instead of snapping each channel independently
vec3 clipAABB(vec3 minc, vec3 maxc, vec3 hist) {
    vec3 center = 0.5 * (maxc + minc);
    vec3 extents = 0.5 * (maxc - minc) + 1e-5;
    vec3 v = hist - center;
    vec3 a = abs(v / extents);
    float t = max(a.x, max(a.y, a.z));
    return (t > 1.0) ? center + v / t : hist;
}

// catmull-rom history filter (5 bilinear fetches, corners dropped), a plain bilinear tap
// re-blurs the accumulation every frame and the image goes soft in motion
vec3 sampleHistory(vec2 uv, vec2 texSize) {
    vec2 samplePos = uv * texSize;
    vec2 texPos1 = floor(samplePos - 0.5) + 0.5;
    vec2 f = samplePos - texPos1;
    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);
    vec2 w12 = w1 + w2;
    vec2 offset12 = w2 / w12;
    vec2 texPos0 = (texPos1 - 1.0) / texSize;
    vec2 texPos3 = (texPos1 + 2.0) / texSize;
    vec2 texPos12 = (texPos1 + offset12) / texSize;
    vec3 result =
        texture(History, vec2(texPos12.x, texPos0.y)).rgb * (w12.x * w0.y) +
        texture(History, vec2(texPos0.x, texPos12.y)).rgb * (w0.x * w12.y) +
        texture(History, vec2(texPos12.x, texPos12.y)).rgb * (w12.x * w12.y) +
        texture(History, vec2(texPos3.x, texPos12.y)).rgb * (w3.x * w12.y) +
        texture(History, vec2(texPos12.x, texPos3.y)).rgb * (w12.x * w3.y);
    float weight = w12.x * w0.y + w0.x * w12.y + w12.x * w12.y + w3.x * w12.y + w12.x * w3.y;
    return max(result / weight, vec3(0.0));
}

float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

void main() {
    vec3 cur = texture(ColorSampler, vUV).rgb;
    if (Feedback <= 0.0) { FragColor = vec4(cur, 1.0); return; }

    // camera-only reprojection, works for everything static (terrain, the far field this
    // pass exists for), moving objects fall back to the neighbourhood clip below
    float depth = texture(DepthSampler, vUV).r;
    vec3 P = reconstruct(vUV, depth);
    vec4 pc = PrevViewProj * vec4(P + EyeDelta, 1.0);
    if (pc.w <= 0.0) { FragColor = vec4(cur, 1.0); return; }
    vec2 puv = pc.xy / pc.w * 0.5 + 0.5;
    if (any(lessThan(puv, vec2(0.0))) || any(greaterThan(puv, vec2(1.0)))) {
        FragColor = vec4(cur, 1.0);
        return;
    }

    vec2 texSize = vec2(textureSize(ColorSampler, 0));
    vec3 hist = sampleHistory(puv, texSize);

    // 3x3 neighbourhood variance box in YCoCg. the box width is the only defence against a moving
    // object's stale history, since reprojection here is camera only, so it is tunable
    ivec2 ip = ivec2(gl_FragCoord.xy);
    ivec2 maxIp = ivec2(texSize) - 1;
    vec3 m1 = vec3(0.0), m2 = vec3(0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec3 c = rgb2ycocg(texelFetch(ColorSampler, clamp(ip + ivec2(x, y), ivec2(0), maxIp), 0).rgb);
            m1 += c;
            m2 += c * c;
        }
    }
    vec3 mu = m1 / 9.0;
    vec3 sigma = sqrt(max(m2 / 9.0 - mu * mu, 0.0));
    vec3 box = sigma * ClipSigma;
    hist = ycocg2rgb(clipAABB(mu - box, mu + box, rgb2ycocg(hist)));

    // less history when the pixel moved far across the screen, and inverse-luminance
    // weighting so a single bright frame (firefly) can't dominate the blend
    float velPx = length((vUV - puv) * texSize);
    float f = Feedback * (1.0 - clamp(velPx / 80.0, 0.0, 0.5));
    float wc = (1.0 - f) / (1.0 + luma(cur));
    float wh = f / (1.0 + luma(hist));
    FragColor = vec4((cur * wc + hist * wh) / max(wc + wh, 1e-5), 1.0);
}
