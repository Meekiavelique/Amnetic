#version 330 core

layout(location = 0) out vec4 FragColor;
layout(location = 1) out vec4 GNormal;
layout(location = 2) out vec4 GMaterial;

void main() {
    FragColor = vec4(0.42, 0.78, 0.85, 1.0);
    GNormal = vec4(0.0, 1.0, 0.0, 1.0);
    GMaterial = vec4(0.05, 0.0, 0.6, 0.0);
}
