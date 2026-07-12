#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D LayerSampler;

// pass-through, the caller alpha-blends the layer over the scene so the layer's own alpha
// drives the composite
void main() {
    FragColor = texture(LayerSampler, vUV);
}
