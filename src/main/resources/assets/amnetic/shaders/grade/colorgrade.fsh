#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D ColorSampler;
uniform sampler2D LutSampler;
uniform int HasLut;
uniform float LutSize;
uniform float LutIntensity;

uniform float Exposure;
uniform float Contrast;
uniform float Saturation;
uniform float Brightness;
uniform float Temperature;
uniform float Tint;
uniform float Gamma;

vec3 applyLut(vec3 c) {
    float s = LutSize;
    c = clamp(c, 0.0, 1.0);
    float texW = s * s;
    float b = c.b * (s - 1.0);
    float slice = floor(b);
    float f = b - slice;
    float u0 = (slice * s + c.r * (s - 1.0) + 0.5) / texW;
    float u1 = (min(slice + 1.0, s - 1.0) * s + c.r * (s - 1.0) + 0.5) / texW;
    float v = (c.g * (s - 1.0) + 0.5) / s;
    vec3 a = texture(LutSampler, vec2(u0, v)).rgb;
    vec3 d = texture(LutSampler, vec2(u1, v)).rgb;
    return mix(a, d, f);
}

void main() {
    vec3 c = texture(ColorSampler, vUV).rgb;

    c *= Exposure;
    c.r += Temperature * 0.1;
    c.b -= Temperature * 0.1;
    c.g += Tint * 0.1;
    c += Brightness;
    c = (c - 0.5) * Contrast + 0.5;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(l), c, Saturation);
    c = pow(max(c, 0.0), vec3(1.0 / max(Gamma, 1e-3)));
    c = clamp(c, 0.0, 1.0);

    if (HasLut == 1) {
        c = mix(c, applyLut(c), LutIntensity);
    }

    FragColor = vec4(c, 1.0);
}
