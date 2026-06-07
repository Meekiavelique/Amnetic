#version 330

#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

out vec3 vPos;
out vec2 vUV;
out vec4 vColor;
out vec3 vNormal;

void main() {
    vec4 clip = ProjMat * ModelViewMat * vec4(Position, 1.0);
    clip.z = min(clip.z, clip.w * 0.999999);   // never far-clip distant geometry
    gl_Position = clip;
    vPos = Position;
    vUV = UV0;
    vColor = Color;
    vNormal = Normal;
}
