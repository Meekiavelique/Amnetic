#version 330 core

layout(location = 0) in vec2 Position;
layout(location = 1) in vec2 UV;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec4 Params;
layout(location = 4) in vec2 Extra;
layout(location = 5) in vec4 Clip;

uniform mat4 Ortho;

out vec2 vUv;
out vec4 vColor;
out vec2 vPos;
flat out vec4 vParams;
flat out vec2 vExtra;
flat out vec4 vClip;

void main() {
    gl_Position = Ortho * vec4(Position, 0.0, 1.0);
    vUv = UV;
    vColor = Color;
    vPos = Position;
    vParams = Params;
    vExtra = Extra;
    vClip = Clip;
}
