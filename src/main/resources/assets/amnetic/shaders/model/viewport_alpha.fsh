#version 330 core

in vec2 vUV;

uniform sampler2D DepthSampler;

out vec4 FragColor;

void main() {
    float d = texture(DepthSampler, vUV).r;
    if (d >= 1.0) discard;
    FragColor = vec4(0.0, 0.0, 0.0, 1.0);
}
