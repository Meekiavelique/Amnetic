#version 330 core

in vec2 quadUV;
in vec4 vColor;

uniform sampler2D TextureSampler;

out vec4 FragColor;

void main() {
    vec4 c = texture(TextureSampler, quadUV) * vColor;
    if (c.a < 0.003) discard;
    FragColor = c;
}
