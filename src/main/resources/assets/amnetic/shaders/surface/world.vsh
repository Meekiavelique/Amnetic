#version 330 core

layout(location = 0) in vec3 Position; // camera-relative
layout(location = 1) in vec2 UV;

uniform mat4 ViewProj;

out vec2 vUv;

void main() {
    gl_Position = ViewProj * vec4(Position, 1.0);
    vUv = UV;
}
