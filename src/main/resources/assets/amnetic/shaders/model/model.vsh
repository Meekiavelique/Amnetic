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

uniform mat4 ProjViewMatrix;
uniform int Skinned;
uniform vec3 CameraPos;
uniform int WorldSpace;
uniform int SourceIndexed;

uniform samplerBuffer JointMatrixTex;
uniform int JointCount;
uniform int MaterialId;
uniform float Time;

out vec3 vNormal;
out vec3 vTangent;
out vec3 vBitangent;
out vec2 vUV;
out vec4 vLight;
out vec3 vWorldPos;

#include "amnetic:shaders/material/custom_vertex_ladder.glsl"

vec3 displacedAt(int id, mat4 model, vec3 localP, vec3 origin, vec2 uv, float t) {
    vec3 worldP = (model * vec4(localP, 1.0)).xyz;
    bool hit;
    return worldP + displaceCustomMaterial(id, VertexSample(localP, worldP, origin, uv, t), hit);
}

mat4 fetchJoint(int base, int joint) {
    int o = (base + joint) * 4;
    return mat4(texelFetch(JointMatrixTex, o + 0),
               texelFetch(JointMatrixTex, o + 1),
               texelFetch(JointMatrixTex, o + 2),
               texelFetch(JointMatrixTex, o + 3));
}

mat4 skinMatrix() {
    int instance = (SourceIndexed == 1) ? int(InstLight.z + 0.5) : gl_InstanceID;
    int base = instance * JointCount;
    return Weights.x * fetchJoint(base, int(Joints.x))
         + Weights.y * fetchJoint(base, int(Joints.y))
         + Weights.z * fetchJoint(base, int(Joints.z))
         + Weights.w * fetchJoint(base, int(Joints.w));
}

void main() {
    mat4 model = mat4(InstModel0, InstModel1, InstModel2, InstModel3);
    if (WorldSpace == 1) {
        model[3].xyz -= CameraPos;
    }

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

    bool displaced;
    vec3 offset = displaceCustomMaterial(MaterialId,
        VertexSample(localPos.xyz, worldPos.xyz, model[3].xyz, UV, Time), displaced);

    vec3 worldNormal = normalize(normalMatrix * localNormal);
    vec3 worldTangent = normalize(normalMatrix * localTangent);

    if (displaced) {
        worldPos.xyz += offset;

        vec3 localBitangent = cross(localNormal, localTangent) * Tangent.w;
        if (dot(localTangent, localTangent) > 1e-8 && dot(localBitangent, localBitangent) > 1e-8) {
            const float eps = 0.05;
            vec3 origin = model[3].xyz;
            vec3 here = worldPos.xyz;
            vec3 alongT = displacedAt(MaterialId, model,
                localPos.xyz + normalize(localTangent) * eps, origin, UV, Time);
            vec3 alongB = displacedAt(MaterialId, model,
                localPos.xyz + normalize(localBitangent) * eps, origin, UV, Time);

            vec3 dT = alongT - here;
            vec3 dB = alongB - here;
            vec3 rebuilt = cross(dT, dB) * Tangent.w;
            if (dot(rebuilt, rebuilt) > 1e-12) {
                worldNormal = normalize(rebuilt);
                worldTangent = normalize(dT - worldNormal * dot(worldNormal, dT));
            }
        }
    }

    vNormal = worldNormal;
    vTangent = worldTangent;
    vBitangent = cross(vNormal, vTangent) * Tangent.w;
    vUV = UV;
    vLight = InstLight;
    vWorldPos = worldPos.xyz;
    gl_Position = ProjViewMatrix * worldPos;
}
