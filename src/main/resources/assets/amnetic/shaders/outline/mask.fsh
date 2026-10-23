#version 330 core

in vec2 vUV;
in vec4 vColor;
in vec3 vView;

uniform sampler2D Sampler0;
uniform int Textured;

layout(location = 0) out vec4 OutColor;
layout(location = 1) out float OutDist;

void main() {
    // cutout sprites, the transparent texels of a flat item aren't part of its shape
    if (Textured == 1 && textureLod(Sampler0, vUV, 0.0).a < 0.1) discard;
    OutColor = vColor;
    // distance from the eye, zero means empty
    OutDist = max(length(vView), 1e-4);
}
