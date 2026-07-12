#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Normal;
layout(location = 2) in vec2 UV;

layout(location = 3) in vec4 InstModel0;
layout(location = 4) in vec4 InstModel1;
layout(location = 5) in vec4 InstModel2;
layout(location = 6) in vec4 InstModel3;
layout(location = 7) in vec4 InstLight;

layout(location = 8) in vec4 Tangent;
layout(location = 9) in vec4 Joints;
layout(location = 10) in vec4 Weights;

const int MAX_JOINTS = 128;

uniform mat4 ProjViewMatrix;
uniform int Skinned;
uniform mat4 JointMatrices[MAX_JOINTS];

out vec3 vNormal;
out vec3 vTangent;
out vec3 vBitangent;
out vec2 vUV;
out vec4 vLight;
out vec3 vWorldPos;

mat4 skinMatrix() {
    return Weights.x * JointMatrices[int(Joints.x)]
         + Weights.y * JointMatrices[int(Joints.y)]
         + Weights.z * JointMatrices[int(Joints.z)]
         + Weights.w * JointMatrices[int(Joints.w)];
}

void main() {
    mat4 model = mat4(InstModel0, InstModel1, InstModel2, InstModel3);

    vec4 localPos = vec4(Position, 1.0);
    vec3 localNormal = Normal;
    vec3 localTangent = Tangent.xyz;
    if (Skinned == 1) {
        mat4 skin = skinMatrix();
        localPos = skin * localPos;
        mat3 skin3 = mat3(skin);
        localNormal = skin3 * localNormal;
        localTangent = skin3 * localTangent;
    }

    mat3 normalMatrix = transpose(inverse(mat3(model)));
    vec4 worldPos = model * localPos;
    vNormal = normalize(normalMatrix * localNormal);
    vTangent = normalize(normalMatrix * localTangent);
    vBitangent = cross(vNormal, vTangent) * Tangent.w;
    vUV = UV;
    vLight = InstLight;
    // instance matrices are camera-relative (ModelRegistry subtracts the camera position), so the
    // camera sits at the origin and the per-fragment view vector is just normalize(-vWorldPos)
    vWorldPos = worldPos.xyz;
    gl_Position = ProjViewMatrix * worldPos;
}
