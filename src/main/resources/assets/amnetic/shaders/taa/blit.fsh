#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D ColorSampler;

void main() {
    FragColor = vec4(texture(ColorSampler, vUV).rgb, 1.0);
}
