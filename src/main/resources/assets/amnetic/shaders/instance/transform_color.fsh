#version 330 core

in vec4 vColor;

out vec4 FragColor;

void main() {
    if (vColor.a < 0.001) discard;
    FragColor = vColor;
}
