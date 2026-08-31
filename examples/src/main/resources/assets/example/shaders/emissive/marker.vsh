#version 330 core

layout(location = 0) in vec3 Position;

uniform mat4 ViewProj;
uniform vec3 Offset;
uniform float Scale;

void main() {
    gl_Position = ViewProj * vec4(Position * Scale + Offset, 1.0);
}
