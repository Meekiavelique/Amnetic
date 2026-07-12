#version 430 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV;

uniform mat4 uViewProj;

out vec2 vUV;

void main() {
    vUV = UV;
    gl_Position = uViewProj * vec4(Position, 1.0);
}
