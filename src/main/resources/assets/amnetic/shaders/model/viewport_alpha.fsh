#version 330 core

in vec2 vUV;

uniform sampler2D DepthSampler;

out vec4 FragColor;

// offscreen model renders carry material alpha (glass writes ~0) which is wrong for ui
// compositing, coverage comes from depth instead: the model wrote depth, so it is there
void main() {
    float d = texture(DepthSampler, vUV).r;
    if (d >= 1.0) discard;
    FragColor = vec4(0.0, 0.0, 0.0, 1.0); // color writes are masked off, only alpha lands
}
