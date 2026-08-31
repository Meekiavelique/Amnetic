#version 330 core

in vec2 vUV;
in vec2 vMaskUV;
in float vStrength;
in float vHasMask;

uniform sampler2D AtlasSampler;
uniform float Intensity;

out vec4 FragColor;

void main() {
    vec4 texel = texture(AtlasSampler, vUV);
    if (texel.a < 0.1) discard;

    float mask = 1.0;
    if (vHasMask > 0.5) {
        vec4 m = texture(AtlasSampler, vMaskUV);
        mask = max(max(m.r, m.g), m.b) * m.a;
        if (mask < 0.004) discard;
    }

    FragColor = vec4(texel.rgb * mask * vStrength * Intensity, 1.0);
}
