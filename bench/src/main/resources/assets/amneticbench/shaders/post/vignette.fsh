#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform VignetteConfig {
    vec3 Tint;
    float Radius;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float depth = texture(DepthSampler, texCoord).r;
    float sky = step(0.9999, depth);
    float d = distance(texCoord, vec2(0.5));
    float v = smoothstep(Radius, Radius - 0.45, d);
    vec3 edge = mix(color * Tint, color, v);
    fragColor = vec4(mix(edge, edge * vec3(0.8, 0.9, 1.2), sky), 1.0);
}
