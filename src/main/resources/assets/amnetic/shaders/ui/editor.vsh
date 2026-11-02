#version 450

layout(location=0) in vec2 Position;
layout(location=1) in vec2 UV;
layout(location=2) in vec4 Color;

layout(std140) uniform EditorParams {
    vec2 DisplayPos;
    vec2 DisplaySize;
};

layout(location=0) out vec2 vUV;
layout(location=1) out vec4 vColor;

void main() {
    vUV = UV;
    vColor = Color;
    vec2 p = (Position - DisplayPos) / DisplaySize;
    gl_Position = vec4(p.x * 2.0 - 1.0, 1.0 - p.y * 2.0, 0.0, 1.0);
}
