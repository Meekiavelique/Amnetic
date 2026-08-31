#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV;
layout(location = 2) in float Strength;

uniform mat4 ViewProj;
uniform vec3 Anchor;

out vec2 vUV;
out float vStrength;

void main() {
    vUV = UV;
    vStrength = Strength;
    gl_Position = ViewProj * vec4(Position + Anchor, 1.0);
}
