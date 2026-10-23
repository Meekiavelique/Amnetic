#include "amnetic:shaders/common/depth.glsl"
uniform vec4 ProjInfo;
uniform vec2 DepthInfo;
uniform int ZeroToOne;

float linearDepth(float d) {
    float ndc = ZeroToOne == 1 ? d : d * 2.0 - 1.0;
    return DepthInfo.y / (ndc + DepthInfo.x);
}

vec3 viewAt(vec2 uv, float z) {
    return vec3((uv * 2.0 - 1.0 + ProjInfo.zw) * ProjInfo.xy * z, -z);
}

float interleaved(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}
