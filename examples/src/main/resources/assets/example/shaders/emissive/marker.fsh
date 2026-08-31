#version 330 core

uniform vec3 GlowColor;

out vec4 FragColor;

void main() {
    FragColor = vec4(GlowColor, 1.0);
}
