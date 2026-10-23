#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ChromaConfig {
    float Strength;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float wobble = sin(GameTime * 24000.0 * 0.05) * 0.5 + 0.5;
    vec2 offset = (texCoord - 0.5) * Strength * (0.5 + wobble) / InSize;
    float r = texture(InSampler, texCoord + offset).r;
    float g = texture(InSampler, texCoord).g;
    float b = texture(InSampler, texCoord - offset).b;
    fragColor = vec4(r, g, b, 1.0);
}
