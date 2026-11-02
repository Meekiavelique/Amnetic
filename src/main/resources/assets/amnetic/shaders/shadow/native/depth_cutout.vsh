#version 450

layout(location=0) in vec3 Position;
layout(location=1) in vec2 UV;

out vec2 vUV;

uniform mat4 uViewProj;
uniform vec4 Tile;

void main() {
    vUV = UV;
    vec4 p = uViewProj * vec4(Position, 1);
#ifdef AMNETIC_ZERO_TO_ONE
    p.z = (p.z + p.w) * 0.5;
#endif
    p.xy = p.xy * Tile.xy + Tile.zw * p.w;
    gl_Position = p;
}
