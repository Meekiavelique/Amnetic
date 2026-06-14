#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D Sampler;
uniform vec2 TexelSize; // 1.0 / source (smaller) resolution
uniform float Radius;   // filter spread in source texels

void main() {
    float x = TexelSize.x * Radius;
    float y = TexelSize.y * Radius;

    vec3 a = texture(Sampler, vUV + vec2(-x,  y)).rgb;
    vec3 b = texture(Sampler, vUV + vec2( 0.0, y)).rgb;
    vec3 c = texture(Sampler, vUV + vec2( x,  y)).rgb;
    vec3 d = texture(Sampler, vUV + vec2(-x,  0.0)).rgb;
    vec3 e = texture(Sampler, vUV).rgb;
    vec3 f = texture(Sampler, vUV + vec2( x,  0.0)).rgb;
    vec3 g = texture(Sampler, vUV + vec2(-x, -y)).rgb;
    vec3 h = texture(Sampler, vUV + vec2( 0.0,-y)).rgb;
    vec3 i = texture(Sampler, vUV + vec2( x, -y)).rgb;

    vec3 col = e * 4.0;
    col += (b + d + f + h) * 2.0;
    col += (a + c + g + i);
    col *= (1.0 / 16.0);
    FragColor = vec4(col, 1.0);
}
