#version 330 core

// Per-vertex (divisor 0)
layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Normal;
layout(location = 2) in vec2 UV;

// Per-instance (divisor 1)
layout(location = 3) in vec4 InstModel0;
layout(location = 4) in vec4 InstModel1;
layout(location = 5) in vec4 InstModel2;
layout(location = 6) in vec4 InstModel3;
layout(location = 7) in vec4 InstLight;   // (blockBrightness, skyBrightness, ao, unused)

uniform mat4 ProjViewMatrix;

out vec3 vNormal;
out vec2 vUV;
out vec4 vLight;

void main() {
    mat4 model = mat4(InstModel0, InstModel1, InstModel2, InstModel3);
    vec4 worldPos = model * vec4(Position, 1.0);
    // mat3(model) is fine for rotation + uniform scale (the common case); normalize handles scale.
    vNormal = normalize(mat3(model) * Normal);
    vUV = UV;
    vLight = InstLight;
    gl_Position = ProjViewMatrix * worldPos;
}
