#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D ColorSampler;
uniform float Sharpness; // 0..1 developer knob

void main() {
    ivec2 sz = textureSize(ColorSampler, 0);
    ivec2 ip = clamp(ivec2(gl_FragCoord.xy), ivec2(1), sz - 2);

    vec3 e = texelFetch(ColorSampler, ip, 0).rgb;
    vec3 b = texelFetch(ColorSampler, ip + ivec2(0, 1), 0).rgb;
    vec3 d = texelFetch(ColorSampler, ip + ivec2(-1, 0), 0).rgb;
    vec3 f = texelFetch(ColorSampler, ip + ivec2(1, 0), 0).rgb;
    vec3 h = texelFetch(ColorSampler, ip + ivec2(0, -1), 0).rgb;

    vec3 mnRGB = min(min(min(d, e), min(f, b)), h);
    vec3 mxRGB = max(max(max(d, e), max(f, b)), h);

    vec3 ampRGB = sqrt(clamp(min(mnRGB, 1.0 - mxRGB) / max(mxRGB, vec3(1e-5)), 0.0, 1.0));

    float peak = -1.0 / mix(8.0, 5.0, clamp(Sharpness, 0.0, 1.0));
    vec3 wRGB = ampRGB * peak;
    vec3 outc = (b * wRGB + d * wRGB + f * wRGB + h * wRGB + e) / (1.0 + 4.0 * wRGB);
    FragColor = vec4(clamp(outc, 0.0, 1.0), 1.0);
}
