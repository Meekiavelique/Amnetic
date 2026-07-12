#version 330 core

in vec2 vUV;

uniform sampler2D AlbedoSampler;
uniform int HasAlbedo;
uniform float AlphaCutoff;

out vec4 FragColor;

void main() {
    if (AlphaCutoff > 0.0 && HasAlbedo == 1) {
        if (texture(AlbedoSampler, vUV).a < AlphaCutoff) discard;
    }
    FragColor = vec4(0.0, 0.0, 0.0, 1.0);
}
