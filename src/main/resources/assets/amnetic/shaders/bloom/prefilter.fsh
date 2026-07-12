#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D Sampler; // scene color, only used as the no-g-buffer fallback
uniform sampler2D DepthSampler; // scene depth, excludes the sky
uniform sampler2D EmissiveSampler; // g-buffer emissive target: rgb = emissive color, a = strength
uniform int HasGBuffer;
uniform float Threshold;
uniform float Knee;

// with a g-buffer the bright-pass reads the emissive target directly, so sky/terrain/gui can never
// bloom no matter how bright they render. threshold/knee still shape a soft-knee curve on top for
// intensity grading. without a g-buffer it falls back to a depth-aware HDR threshold on scene color
void main() {
    if (texture(DepthSampler, vUV).r >= 1.0) discard;

    vec3 c;
    if (HasGBuffer == 1) {
        vec4 em = texture(EmissiveSampler, vUV);
        if (em.a <= 0.0) discard;
        c = em.rgb; // already true HDR emissive, alpha is just the coverage mask
    } else {
        c = texture(Sampler, vUV).rgb;
    }

    float br = max(max(c.r, c.g), c.b);
    if (br <= 1e-4) discard;
    float k = Knee * Threshold + 1e-4;
    float soft = clamp(br - Threshold + k, 0.0, 2.0 * k);
    soft = (soft * soft) / (4.0 * k);
    float contrib = clamp(max(soft, br - Threshold) / max(br, 1e-4), 0.0, 1.0);
    if (contrib <= 1e-4) discard; // below the knee, discarding lets the occlusion query skip empty frames
    FragColor = vec4(c * contrib, 1.0);
}
