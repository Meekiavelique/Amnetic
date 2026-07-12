#version 330 core

in vec4 vClip;
in vec2 vLocalUV;
in vec2 vLocalPos;
flat in vec4 vParams;

uniform sampler2D CaptureSampler;
uniform sampler2D MaskSampler;

out vec4 FragColor;

void main() {
    float samplingMode = vParams.x;
    float maskMode = vParams.y;
    float feather = vParams.z;
    float radius = vParams.w;

    vec2 fitUV = clamp(vLocalUV, 0.0, 1.0);
    vec2 screenUV = (vClip.xy / vClip.w) * 0.5 + 0.5;
    vec2 uv = samplingMode > 0.5 ? screenUV : fitUV;

    vec3 color = texture(CaptureSampler, uv).rgb;

    float alpha = 1.0;
    if (maskMode > 1.5) {
        alpha = texture(MaskSampler, vLocalUV).a;
    } else if (maskMode > 0.5) {
        float d = length(vLocalPos);
        float edge = max(feather, 1e-4);
        alpha = 1.0 - smoothstep(radius - edge, radius, d);
    }

    if (alpha <= 0.001) discard;
    FragColor = vec4(color, alpha);
}
