#version 330 core

// Amnetic particle base vertex shader (velocity-aligned, stretched). The quad is oriented so its
// length axis (Position.y) runs along the screen-projected velocity and stretches with speed; its
// width axis (Position.x) stays perpendicular. For sparks, rain, debris streaks. Same fragment
// shader contract as particle/billboard.vsh.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV;

layout(location = 2) in vec3 Center;     // camera-relative
layout(location = 3) in vec3 Velocity;   // world-space velocity
layout(location = 4) in float Size;
layout(location = 5) in float Rotation;  // unused in stretched mode
layout(location = 6) in vec4 InstColor;
layout(location = 7) in vec4 SeedAge;

uniform mat4 ProjectionMatrix;
uniform mat4 ViewMatrix;

const float STRETCH_SCALE = 0.15;   // blocks/sec -> extra length factor
const float MAX_STRETCH   = 3.0;

out vec2 quadUV;
out vec4 vColor;
out vec2 seed;
out float vAge;

void main() {
    vec4 viewCenter = ViewMatrix * vec4(Center, 1.0);

    vec3 viewVel = mat3(ViewMatrix) * Velocity;
    vec2 dir = viewVel.xy;
    float speed = length(dir);
    vec2 vdir = speed > 1e-4 ? dir / speed : vec2(0.0, 1.0);
    vec2 vperp = vec2(-vdir.y, vdir.x);

    float stretch = Size * (1.0 + clamp(speed * STRETCH_SCALE, 0.0, MAX_STRETCH));

    vec2 corner = Position.xy;
    vec2 offset = vperp * (corner.x * Size) + vdir * (corner.y * stretch);

    gl_Position = ProjectionMatrix * vec4(viewCenter.xy + offset, viewCenter.z, viewCenter.w);

    quadUV = UV;
    vColor = InstColor;
    seed = SeedAge.xy;
    vAge = SeedAge.z;
}
