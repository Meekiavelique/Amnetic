#version 410 core

layout(location = 0) in vec3 Position;
layout(location = 2) in vec2 UV0;
layout(location = 4) in vec3 Normal;

uniform mat4 ViewProj;
uniform vec3 ChunkOffset;

out vec2 vUV;
out vec3 vNormal;
out vec3 vPos;

void main() {
    vPos = Position + ChunkOffset;
    gl_Position = ViewProj * vec4(vPos, 1.0);
    vUV = UV0;
    vNormal = Normal;
}
