#version 330 core

in vec4 vClip;

uniform sampler2D ReflectionSampler;

out vec4 FragColor;

void main() {
    vec2 uv = (vClip.xy / vClip.w) * 0.5 + 0.5;
    FragColor = vec4(texture(ReflectionSampler, uv).rgb, 1.0);
}
