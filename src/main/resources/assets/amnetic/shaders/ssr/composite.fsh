#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D ReflectionSampler; // rgb = reflected colour, a = strength

// straight 1:1 sample. temporal accumulation already denoises the reflection, blurring here again
// is what made SSR look blurry
void main() {
    FragColor = texture(ReflectionSampler, vUV);
}
