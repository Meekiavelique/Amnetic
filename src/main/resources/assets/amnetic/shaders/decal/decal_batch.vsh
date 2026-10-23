#version 330 core

#define BATCH 32

uniform vec4 Rects[BATCH]; // screen rect per decal in ndc, x0 y0 x1 y1

flat out int Index;

void main() {
    vec4 r = Rects[gl_InstanceID];
    vec2 c = vec2(float(gl_VertexID & 1), float((gl_VertexID >> 1) & 1));
    gl_Position = vec4(mix(r.xy, r.zw, c), 0.0, 1.0);
    Index = gl_InstanceID;
}
