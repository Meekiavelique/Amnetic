#version 330 core

in vec2 quadUV;
in vec4 vColor;

out vec4 FragColor;

void main() {
    float d = length(quadUV - 0.5) * 2.0; // 0 at centre, 1 at edge
    float a = 1.0 - smoothstep(0.45, 1.0, d); // soft radial falloff
    a *= a;
    vec4 c = vColor;
    c.a *= a;
    if (c.a < 0.003) discard;
    FragColor = c;
}
