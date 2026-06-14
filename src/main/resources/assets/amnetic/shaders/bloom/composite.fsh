#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D Sampler;
uniform float Intensity;

void main() {
    vec3 c = texture(Sampler, vUV).rgb * Intensity;
    FragColor = vec4(c, 1.0);
}
