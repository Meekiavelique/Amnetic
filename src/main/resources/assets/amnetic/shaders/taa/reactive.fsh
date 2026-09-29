#version 410 core

in vec2 vUV;
out vec4 Mask;

uniform sampler2D Before;
uniform sampler2D After;

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    vec3 d = abs(texelFetch(After, p, 0).rgb - texelFetch(Before, p, 0).rgb);
    Mask = vec4(clamp(max(d.r, max(d.g, d.b)) * 6.0, 0.0, 1.0));
}
