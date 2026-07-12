#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 2) in vec2 UV;

layout(location = 3) in vec4 InstModel0;
layout(location = 4) in vec4 InstModel1;
layout(location = 5) in vec4 InstModel2;
layout(location = 6) in vec4 InstModel3;

layout(location = 9) in vec4 Joints;
layout(location = 10) in vec4 Weights;

const int MAX_JOINTS = 128;

uniform mat4 ProjViewMatrix;
uniform int Skinned;
uniform mat4 JointMatrices[MAX_JOINTS];

out vec2 vUV;

mat4 skinMatrix() {
    return Weights.x * JointMatrices[int(Joints.x)]
         + Weights.y * JointMatrices[int(Joints.y)]
         + Weights.z * JointMatrices[int(Joints.z)]
         + Weights.w * JointMatrices[int(Joints.w)];
}

void main() {
    mat4 model = mat4(InstModel0, InstModel1, InstModel2, InstModel3);

    vec4 localPos = vec4(Position, 1.0);
    if (Skinned == 1) {
        localPos = skinMatrix() * localPos;
    }

    vUV = UV;
    gl_Position = ProjViewMatrix * (model * localPos);
}
