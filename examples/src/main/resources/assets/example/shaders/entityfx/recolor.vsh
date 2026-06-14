#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;

out vec2 vUV;

void main() {
    vUV = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
