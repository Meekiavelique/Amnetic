#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV;

layout(location = 2) in vec3 Center;
layout(location = 3) in float Size;
layout(location = 4) in vec4 InstColor;
layout(location = 5) in vec4 UvRect;

uniform mat4 ProjectionMatrix;
uniform mat4 ViewMatrix;

out vec2 vUV;
out vec4 vColor;

void main() {
    vec4 viewCenter = ViewMatrix * vec4(Center, 1.0);
    vec2 viewOffset = Position.xy * Size;
    gl_Position = ProjectionMatrix * vec4(viewCenter.xy + viewOffset, viewCenter.z, viewCenter.w);
    vUV = mix(UvRect.xy, UvRect.zw, UV);
    vColor = InstColor;
}
