#version 410 core

in vec2 vUV;

uniform sampler2D uAtlas;

out vec4 FragColor;

void main() {
    if (texture(uAtlas, vUV).a < 0.5) discard;
    FragColor = vec4(0.0, 0.0, 0.0, 1.0);
}
