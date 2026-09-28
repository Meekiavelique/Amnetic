#version 330 core

in vec2 vUV;

uniform sampler2D AlbedoSampler;
uniform int HasAlbedo;
uniform float AlphaCutoff;

out vec4 FragColor;

void main() {
    float cutoff = AlphaCutoff < 0.0 ? 0.5 : AlphaCutoff;
    if (cutoff > 0.0 && HasAlbedo == 1) {
        if (texture(AlbedoSampler, vUV).a < cutoff) discard;
    }
    FragColor = vec4(0.0, 0.0, 0.0, 1.0);
}
