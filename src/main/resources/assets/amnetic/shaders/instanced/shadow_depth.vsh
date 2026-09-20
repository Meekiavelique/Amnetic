#version 330 core

layout(location = 0) in vec3 Position;

layout(location = 1) in vec4 InstTransform0;
layout(location = 2) in vec4 InstTransform1;
layout(location = 3) in vec4 InstTransform2;
layout(location = 4) in vec4 InstTransform3;

layout(location = 7) in float InstCastsShadow;

uniform mat4 ProjViewMatrix;
uniform vec3 CameraPos;
uniform int WorldSpace;
uniform int PerInstanceCast;

void main() {
    if (PerInstanceCast == 1 && InstCastsShadow < 0.5) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
    mat4 model = mat4(InstTransform0, InstTransform1, InstTransform2, InstTransform3);
    if (WorldSpace == 1) {
        model[3].xyz -= CameraPos;
    }
    gl_Position = ProjViewMatrix * model * vec4(Position, 1.0);
}
