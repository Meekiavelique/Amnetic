#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D Sampler;
uniform vec2 TexelSize; // 1.0 / source resolution

void main() {
    float x = TexelSize.x;
    float y = TexelSize.y;

    vec3 a = texture(Sampler, vUV + vec2(-2.0 * x,  2.0 * y)).rgb;
    vec3 b = texture(Sampler, vUV + vec2( 0.0,      2.0 * y)).rgb;
    vec3 c = texture(Sampler, vUV + vec2( 2.0 * x,  2.0 * y)).rgb;
    vec3 d = texture(Sampler, vUV + vec2(-2.0 * x,  0.0)).rgb;
    vec3 e = texture(Sampler, vUV).rgb;
    vec3 f = texture(Sampler, vUV + vec2( 2.0 * x,  0.0)).rgb;
    vec3 g = texture(Sampler, vUV + vec2(-2.0 * x, -2.0 * y)).rgb;
    vec3 h = texture(Sampler, vUV + vec2( 0.0,     -2.0 * y)).rgb;
    vec3 i = texture(Sampler, vUV + vec2( 2.0 * x, -2.0 * y)).rgb;
    vec3 j = texture(Sampler, vUV + vec2(-x,  y)).rgb;
    vec3 k = texture(Sampler, vUV + vec2( x,  y)).rgb;
    vec3 l = texture(Sampler, vUV + vec2(-x, -y)).rgb;
    vec3 m = texture(Sampler, vUV + vec2( x, -y)).rgb;

    vec3 col = e * 0.125;
    col += (a + c + g + i) * 0.03125;
    col += (b + d + f + h) * 0.0625;
    col += (j + k + l + m) * 0.125;
    FragColor = vec4(col, 1.0);
}
