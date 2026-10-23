#version 150

uniform sampler2D DiffuseSampler;
in vec2 texCoord;
out vec4 fragColor;

uniform AmneticParams { vec4 Params; };
#define T (Params.x)
#define PI 3.14159265359

vec2 RES;
float def(float a) { return (RES.y / 450.0) * a; }

float gold(vec2 xy, float seed) {
    return fract(sin(dot(xy * seed, vec2(12.9898, 78.233))) * 43758.5453);
}

vec2 tracking(float speed, float offset, float jitter, vec2 fc) {
    float t = 1.0 - mod(T, speed) / speed;
    float start = mod(t * RES.y, RES.y) + gold(vec2(5000.0, 5000.0), 10.0 + fract(T)) * jitter;
    return (fc.y > start) ? (fc + vec2(offset, 0.0)) / RES : fc / RES;
}

vec2 wave(float freq, float offset, vec2 fc, vec2 uv) {
    float phase = floor(fc.y / (RES.y / freq));
    float onm = gold(vec2(1.0 + phase, phase), 10.0);
    float o = sin((uv.y + fract(T * 0.05)) * PI * 2.0 * freq) * ((offset * onm) / RES.x);
    return uv + vec2(o, 0.0);
}

vec4 warpBottom(float height, float offset, float jitterExtent, vec2 uv) {
    float uvH = height / RES.y;
    if (uv.y > uvH) return texture(DiffuseSampler, uv);
    float t = uv.y / uvH;
    float ox = t * (offset / RES.x);
    float jx = (gold(vec2(500.0, 500.0), fract(T)) * jitterExtent) / RES.x;
    uv.x = uv.x - ox - jx;
    return texture(DiffuseSampler, uv) * t;
}

vec4 whiteNoise(float thickness, float opacity, vec4 pixel, vec2 fc) {
    if (gold(vec2(600.0, 500.0), fract(T) * 10.0) > 0.97) {
        float ls = floor(gold(vec2(800.0, 50.0), fract(T)) * RES.y);
        if (floor(fc.y) >= ls && floor(fc.y) < ls + thickness) {
            float freq = gold(vec2(850.0, 50.0), fract(T)) * 3.0 + 1.0;
            float off = gold(vec2(900.0, 51.0), fract(T));
            float x = floor(fc.x) / floor(RES.x) + off;
            float white = pow(cos(PI * fract(x * freq) / 2.0), 10.0) * opacity;
            float grit = gold(vec2(floor(fc.x / 3.0), 800.0), fract(T));
            white = max(white - grit * 0.3, 0.0);
            return pixel + white;
        }
    }
    return pixel;
}

float softLight(float b, float bl) {
    return (bl < 0.5) ? (2.0 * b * bl + b * b * (1.0 - 2.0 * bl)) : (sqrt(b) * (2.0 * bl - 1.0) + 2.0 * b * (1.0 - bl));
}

const float curveAmount = 0.07;
vec2 barrel(vec2 uv) {
    uv = uv * 2.0 - 1.0;
    uv *= 1.0 + dot(uv, uv) * curveAmount;
    return uv * 0.5 + 0.5;
}

void main() {
    RES = vec2(textureSize(DiffuseSampler, 0));

    vec2 cuv = barrel(texCoord);
    vec2 sm = smoothstep(vec2(0.0), vec2(0.006), cuv) * smoothstep(vec2(0.0), vec2(0.006), 1.0 - cuv);
    float screenMask = sm.x * sm.y;
    cuv = clamp(cuv, 0.0, 1.0);
    vec2 fc = cuv * RES;

    vec2 uv = tracking(8.0, def(8.0), 20.0, fc);
    uv = wave(70.0, def(1.0), fc, uv);

    vec4 pixel = warpBottom(def(15.0), def(100.0), def(50.0), uv);
    pixel = whiteNoise(def(6.0), 0.3, pixel, fc);

    float gsize = max(1.0, def(4.0));
    vec2 gc = floor(fc / gsize);
    float seed = floor(fract(T) * 30.0) / 30.0 + 1.0;
    float n = gold(gc + 1.0, seed);
    vec3 grain = vec3(n);
    vec3 blended = vec3(softLight(pixel.r, grain.r), softLight(pixel.g, grain.g), softLight(pixel.b, grain.b));
    pixel.rgb = mix(pixel.rgb, blended, 0.05);

    float vig = pow(cuv.x * (1.0 - cuv.x) * cuv.y * (1.0 - cuv.y), 0.25) * 2.2;
    pixel.rgb *= clamp(vig, 0.0, 1.0) * screenMask;

    fragColor = vec4(pixel.rgb, 1.0);
}
