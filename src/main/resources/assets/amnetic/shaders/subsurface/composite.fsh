#version 430 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D OriginalSampler;
uniform sampler2D DiffusedSampler;
uniform sampler2D GMaterialSampler;

layout(std430, binding = 1) readonly buffer MaterialParamData { vec4 materialParams[]; };

void main() {
    vec4 original = texture(OriginalSampler, vUV);

    int materialId = int(texture(GMaterialSampler, vUV).z * 255.0 + 0.5);
    vec4 tint = materialParams[materialId * 3 + 0];
    float strength = tint.a;

    if (strength <= 0.0) {
        FragColor = original;
        return;
    }

    vec3 diffused = texture(DiffusedSampler, vUV).rgb * tint.rgb;
    FragColor = vec4(mix(original.rgb, diffused, clamp(strength, 0.0, 1.0)), original.a);
}
