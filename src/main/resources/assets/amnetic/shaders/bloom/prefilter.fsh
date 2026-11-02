#version 330 core
#include "amnetic:shaders/common/depth.glsl"

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D Sampler;
uniform sampler2D DepthSampler;
uniform sampler2D EmissiveSampler;
uniform int HasGBuffer;
uniform float Threshold;
uniform float Knee;

void main() {
    if (IS_SKY(texture(DepthSampler, vUV).r)) discard;

    vec3 color = texture(Sampler, vUV).rgb;
    if (HasGBuffer == 1) {
        vec4 emissive = texture(EmissiveSampler, vUV);
        if (emissive.a > 0.0) color = max(color, emissive.rgb);
    }

    float brightness = max(max(color.r, color.g), color.b);
    if (brightness <= 1e-4) discard;
    float knee = Knee * Threshold + 1e-4;
    float soft = clamp(brightness - Threshold + knee, 0.0, 2.0 * knee);
    soft = (soft * soft) / (4.0 * knee);
    float contribution = clamp(max(soft, brightness - Threshold) / brightness, 0.0, 1.0);
    if (contribution <= 1e-4) discard;
    FragColor = vec4(color * contribution, 1.0);
}
