#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D AoSampler;

// straight 1:1 sample of the already-denoised AO, blurring a second time here only widened it
// into a halo across silhouettes. output is the multiply source (scene *= AO)
void main() {
    FragColor = vec4(vec3(texture(AoSampler, vUV).r), 1.0);
}
