#version 430 core

in vec2 vUV;

uniform sampler2D uAtlas; // block atlas

out vec4 FragColor;

void main() {
    vec4 c = texture(uAtlas, vUV);
    if (c.a < 0.05) discard;
    FragColor = vec4(c.rgb, 1.0);
}
