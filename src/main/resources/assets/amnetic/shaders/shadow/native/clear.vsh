#version 450

uniform vec4 Tile;

void main() {
    vec2 p = vec2(float((gl_VertexIndex << 1) & 2), float(gl_VertexIndex & 2)) * 2 - 1;
    gl_Position = vec4(p * Tile.xy + Tile.zw, 1, 1);
}
