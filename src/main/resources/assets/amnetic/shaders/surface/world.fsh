#version 330 core

in vec2 vUv;

uniform sampler2D Canvas;
uniform float Opacity;

out vec4 FragColor;

void main() {
    vec4 c = texture(Canvas, vUv);
    if (c.a <= 0.004) discard;
    FragColor = vec4(c.rgb, c.a * Opacity);
}
