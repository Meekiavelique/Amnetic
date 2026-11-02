#version 450

layout(location=0) in vec2 vUV;
layout(location=1) in vec4 vColor;

uniform sampler2D EditorTexture;

layout(location=0) out vec4 FragColor;

void main() {
    FragColor = vColor * texture(EditorTexture, vUV);
}
