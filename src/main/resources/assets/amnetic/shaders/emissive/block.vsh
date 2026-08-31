#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV;
layout(location = 2) in vec2 MaskUV;
layout(location = 3) in vec2 Params;

uniform mat4 ViewProj;
uniform vec3 Anchor;

out vec2 vUV;
out vec2 vMaskUV;
out float vStrength;
out float vHasMask;

void main() {
    vUV = UV;
    vMaskUV = MaskUV;
    vStrength = Params.x;
    vHasMask = Params.y;
    gl_Position = ViewProj * vec4(Position + Anchor, 1.0);
}
