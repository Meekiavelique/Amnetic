#version 330 core

in vec2 vUV;
in float vStrength;

uniform sampler2D AtlasSampler;
uniform float Intensity;

out vec4 FragColor;

void main() {
    vec4 texel = texture(AtlasSampler, vUV);
    if (texel.a < 0.1) discard;
    FragColor = vec4(texel.rgb * vStrength * Intensity, 1.0);
}
