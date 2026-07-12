#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV;

layout(location = 2) in vec3 Center; // camera-relative
layout(location = 3) in vec3 Velocity; // world-space velocity
layout(location = 4) in float Size;
layout(location = 5) in float Rotation; // unused in stretched mode
layout(location = 6) in vec4 InstColor;
layout(location = 7) in vec4 SeedAge;
layout(location = 8) in vec4 UvRect; // flipbook sub-rect: offset.xy, scale.xy

uniform mat4 ProjectionMatrix;
uniform mat4 ViewMatrix;

const float STRETCH_SCALE = 0.20; // blocks/sec -> extra length factor
const float MAX_STRETCH = 8.0;

out vec2 quadUV;
out vec4 vColor;
out vec2 seed;
out float vAge;
out vec3 vViewPos; // view-space fragment position (distance/fog/fresnel/soft-depth)
out vec3 vAxisView; // view-space streak length axis (fresnel/glancing)

void main() {
    vec4 viewCenter = ViewMatrix * vec4(Center, 1.0);
    vec3 vc = viewCenter.xyz;

    vec3 viewVel = mat3(ViewMatrix) * Velocity;
    float speed = length(viewVel);
    vec3 axis = speed > 1e-4 ? viewVel / speed : vec3(0.0, 1.0, 0.0);
    vec3 toCam = normalize(-vc);
    vec3 side = cross(axis, toCam);
    float sl = length(side);
    side = sl > 1e-4 ? side / sl : vec3(1.0, 0.0, 0.0);

    float stretch = Size * (1.0 + clamp(speed * STRETCH_SCALE, 0.0, MAX_STRETCH));

    vec2 corner = Position.xy;
    vec3 offset = side * (corner.x * Size) + axis * (corner.y * stretch);

    gl_Position = ProjectionMatrix * vec4(vc + offset, viewCenter.w);

    vViewPos = vc + offset;
    vAxisView = axis;
    quadUV = UvRect.xy + UV * UvRect.zw; // flipbook frame sub-rect (whole texture when (0,0,1,1))
    vColor = InstColor;
    seed = SeedAge.xy;
    vAge = SeedAge.z;
}
