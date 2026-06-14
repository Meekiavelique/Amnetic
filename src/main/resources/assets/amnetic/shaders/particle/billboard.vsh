#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV;

layout(location = 2) in vec3 Center;     // camera-relative
layout(location = 3) in vec3 Velocity;   // unused here (see stretched variant)
layout(location = 4) in float Size;
layout(location = 5) in float Rotation;
layout(location = 6) in vec4 InstColor;
layout(location = 7) in vec4 SeedAge;

uniform mat4 ProjectionMatrix;
uniform mat4 ViewMatrix;

out vec2 quadUV;
out vec4 vColor;
out vec2 seed;
out float vAge;
out vec3 vCenter;   // camera-relative particle center

void main() {
    vec4 viewCenter = ViewMatrix * vec4(Center, 1.0);

    float s = sin(Rotation);
    float c = cos(Rotation);
    vec2 corner = Position.xy;
    vec2 rotated = vec2(corner.x * c - corner.y * s, corner.x * s + corner.y * c);
    vec2 offset = rotated * Size;

    gl_Position = ProjectionMatrix * vec4(viewCenter.xy + offset, viewCenter.z, viewCenter.w);

    quadUV = UV;
    vColor = InstColor;
    seed = SeedAge.xy;
    vAge = SeedAge.z;
    vCenter = Center;
}
