#version 330 core

layout(location = 0) in vec3 Position;

uniform mat4 Mvp;

void main() {
    gl_Position = Mvp * vec4(Position, 1.0);
}
