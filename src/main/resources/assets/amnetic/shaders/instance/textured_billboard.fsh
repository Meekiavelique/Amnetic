#version 330 core

in vec2 vUV;
in vec4 vColor;

uniform sampler2D TextureSampler;

out vec4 FragColor;

void main() {
    vec4 tex = texture(TextureSampler, vUV);
    vec4 color = tex * vColor;
    if (color.a < 0.01) discard;
    FragColor = color;
}
