#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV;

layout(location = 2) in vec3 Center;
layout(location = 3) in vec3 Velocity;
layout(location = 4) in float Size;
layout(location = 5) in float Rotation;
layout(location = 6) in vec4 InstColor;
layout(location = 7) in vec4 SeedAge;
layout(location = 8) in vec4 UvRect;

uniform mat4 ProjectionMatrix;
uniform mat4 ViewMatrix;

out vec2 quadUV;
out vec4 vColor;
out vec2 seed;
out float vAge;
out vec3 vViewPos;
out vec3 vAxisView;

void main() {
    vec4 viewCenter = ViewMatrix * vec4(Center, 1.0);
    vec3 vc = viewCenter.xyz;

    vec3 viewSpan = mat3(ViewMatrix) * Velocity;
    float len = length(viewSpan);
    vec3 axis = len > 1e-4 ? viewSpan / len : vec3(0.0, 1.0, 0.0);

    vec3 toCam = normalize(-vc);
    vec3 side = cross(axis, toCam);
    float sl = length(side);
    side = sl > 1e-4 ? side / sl : normalize(cross(axis, vec3(0.0, 0.0, 1.0)));

    vec2 corner = Position.xy;
    vec3 offset = side * (corner.x * Size) + viewSpan * corner.y;

    gl_Position = ProjectionMatrix * vec4(vc + offset, viewCenter.w);

    vec2 beamUV = vec2(fract(UV.y + Rotation), UV.x);
    quadUV = UvRect.xy + beamUV * UvRect.zw;

    vViewPos = vc + offset;
    vAxisView = axis;
    vColor = InstColor;
    seed = SeedAge.xy;
    vAge = SeedAge.z;
}
