#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

uniform mat4 Proj;
uniform mat4 View;

out vec2 vUV;
out vec4 vColor;
out vec3 vView;

void main() {
    vec4 view = View * vec4(Position, 1.0);
    vView = view.xyz;
    vUV = UV0;
    vColor = Color;
    gl_Position = Proj * view;
}
