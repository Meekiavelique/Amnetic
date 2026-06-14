#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 vViewPos;
out vec3 vViewNormal;
out vec4 vColor;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    vViewPos = viewPos.xyz;
    vViewNormal = mat3(ModelViewMat) * Normal;
    vColor = Color;
    gl_Position = ProjMat * viewPos;
}
