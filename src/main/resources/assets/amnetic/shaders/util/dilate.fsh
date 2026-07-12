#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D Source;
uniform vec2 Texel;
uniform int Radius;

const int MAX_RADIUS = 16;

void main() {
    vec4 m = vec4(0.0);
    for (int x = -MAX_RADIUS; x <= MAX_RADIUS; x++) {
        if (abs(x) > Radius) {
            continue;
        }
        for (int y = -MAX_RADIUS; y <= MAX_RADIUS; y++) {
            if (abs(y) > Radius) {
                continue;
            }
            m = max(m, texture(Source, vUV + vec2(float(x), float(y)) * Texel));
        }
    }
    FragColor = m;
}
