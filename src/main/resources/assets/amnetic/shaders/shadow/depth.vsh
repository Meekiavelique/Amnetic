#version 430 core

layout(location = 0) in vec3 Position;

uniform mat4 uViewProj;

void main() {
    gl_Position = uViewProj * vec4(Position, 1.0);
}
