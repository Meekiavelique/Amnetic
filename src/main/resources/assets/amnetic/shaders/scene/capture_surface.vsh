#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 InstTransform0;
layout(location = 2) in vec4 InstTransform1;
layout(location = 3) in vec4 InstTransform2;
layout(location = 4) in vec4 InstTransform3;
layout(location = 5) in vec4 InstParams;

uniform mat4 ProjViewMatrix;

out vec4 vClip;
out vec2 vLocalUV;
out vec2 vLocalPos;
flat out vec4 vParams;

void main() {
    mat4 model = mat4(InstTransform0, InstTransform1, InstTransform2, InstTransform3);
    gl_Position = ProjViewMatrix * model * vec4(Position, 1.0);
    vClip = gl_Position;
    vLocalUV = Position.xz + 0.5;
    vLocalPos = Position.xz * 2.0;
    vParams = InstParams;
}
