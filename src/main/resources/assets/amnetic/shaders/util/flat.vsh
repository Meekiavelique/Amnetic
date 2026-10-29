#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 9) in vec4 Joints;
layout(location = 10) in vec4 Weights;

const int MAX_JOINTS = 128;

uniform mat4 Mvp;
uniform int Skinned;
uniform mat4 JointMatrices[MAX_JOINTS];

void main() {
    vec4 local = vec4(Position, 1.0);
    if (Skinned == 1) {
        local = (Weights.x * JointMatrices[int(Joints.x)]
               + Weights.y * JointMatrices[int(Joints.y)]
               + Weights.z * JointMatrices[int(Joints.z)]
               + Weights.w * JointMatrices[int(Joints.w)]) * local;
    }
    gl_Position = Mvp * local;
}
