#version 430 core

layout(location = 0) in vec3 Position;

uniform mat4 ViewProjVolume;
uniform vec3 LightPos;
uniform float LightRadius;

void main() {
    gl_Position = ViewProjVolume * vec4(Position * LightRadius + LightPos, 1.0);
}
