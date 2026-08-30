#version 430 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D ColorSampler;
uniform sampler2D DepthSampler;
uniform sampler2D GMaterialSampler;

uniform mat4 InvViewProj;
uniform mat4 View;
uniform int ZeroToOne;
uniform vec2 Direction;
uniform vec2 ScreenSize;
uniform float ProjScale;

layout(std430, binding = 1) readonly buffer MaterialParamData { vec4 materialParams[]; };

const float PI = 3.14159265359;
const int TAPS = 11;

#include "amnetic:shaders/common/screen.glsl"

float burley(float r, float d) {
    float rr = max(r, 1e-4);
    float dd = max(d, 1e-4);
    return (exp(-rr / dd) + exp(-rr / (3.0 * dd))) / (8.0 * PI * dd * rr);
}

void main() {
    vec4 centre = texture(ColorSampler, vUV);
    float depth = texture(DepthSampler, vUV).r;

    if (depth >= 1.0) {
        FragColor = centre;
        return;
    }

    int materialId = int(texture(GMaterialSampler, vUV).z * 255.0 + 0.5);
    vec4 tint = materialParams[materialId * 3 + 0];
    vec4 shape = materialParams[materialId * 3 + 1];

    float strength = tint.a;
    float radius = shape.x;
    float nearAmp = shape.z;
    float nearRadius = shape.w;

    if (strength <= 0.0 || radius <= 0.0) {
        FragColor = centre;
        return;
    }

    vec3 P0 = reconstruct(vUV, depth);
    float viewZ = max(-(View * vec4(P0, 1.0)).z, 1e-3);

    float pixels = clamp(ProjScale * radius / viewZ, 1.0, 96.0);
    vec2 step = Direction * pixels / ScreenSize;

    float w0 = burley(0.0, radius) + nearAmp;
    vec3 sum = centre.rgb * w0;
    float wsum = w0;

    for (int i = 1; i <= TAPS; i++) {
        float t = float(i) / float(TAPS);
        float offset = t * t;

        for (int s = -1; s <= 1; s += 2) {
            vec2 uv = vUV + step * offset * float(s);
            if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) continue;

            float d = texture(DepthSampler, uv).r;
            if (d >= 1.0) continue;

            int id = int(texture(GMaterialSampler, uv).z * 255.0 + 0.5);
            if (id != materialId) continue;

            vec3 P = reconstruct(uv, d);
            float r = length(P - P0);

            float w = burley(r, radius) + nearAmp * exp(-r / max(nearRadius, 1e-4));
            sum += texture(ColorSampler, uv).rgb * w;
            wsum += w;
        }
    }

    FragColor = vec4(sum / max(wsum, 1e-6), centre.a);
}
