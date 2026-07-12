#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D Source;
uniform vec2 Texel;
uniform float Thickness;

void main() {
    float c = texture(Source, vUV).a;
    float maxN = 0.0;
    maxN = max(maxN, texture(Source, vUV + vec2(Texel.x, 0.0) * Thickness).a);
    maxN = max(maxN, texture(Source, vUV - vec2(Texel.x, 0.0) * Thickness).a);
    maxN = max(maxN, texture(Source, vUV + vec2(0.0, Texel.y) * Thickness).a);
    maxN = max(maxN, texture(Source, vUV - vec2(0.0, Texel.y) * Thickness).a);
    maxN = max(maxN, texture(Source, vUV + Texel * Thickness).a);
    maxN = max(maxN, texture(Source, vUV - Texel * Thickness).a);
    maxN = max(maxN, texture(Source, vUV + vec2(Texel.x, -Texel.y) * Thickness).a);
    maxN = max(maxN, texture(Source, vUV + vec2(-Texel.x, Texel.y) * Thickness).a);
    FragColor = vec4(clamp(maxN - c, 0.0, 1.0));
}
