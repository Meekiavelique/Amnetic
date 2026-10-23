#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D MaskColor;
uniform sampler2D MaskDist;
uniform sampler2D SceneDepth;
uniform mat4 InvProj;
uniform int ZeroToOne;
uniform float Width;
uniform vec2 Texel;

const int MAX_RADIUS = 8;

float sceneDist(vec2 uv) {
    float d = texture(SceneDepth, uv).r;
    float z = (ZeroToOne == 1) ? d : d * 2.0 - 1.0;
    vec4 p = InvProj * vec4(uv * 2.0 - 1.0, z, 1.0);
    // far plane of an infinite projection, nothing there can hide the outline
    if (abs(p.w) < 1e-6) return 1e9;
    return length(p.xyz / p.w);
}

void main() {
    // only the ring outside the shape, the shape itself stays untouched
    if (texture(MaskDist, vUV).r > 0.0) discard;

    float reach = Width + 0.5;
    int r = min(MAX_RADIUS, int(ceil(reach)));
    float best = 1e9;
    vec2 bestUv = vUV;
    for (int x = -MAX_RADIUS; x <= MAX_RADIUS; x++) {
        if (abs(x) > r) continue;
        for (int y = -MAX_RADIUS; y <= MAX_RADIUS; y++) {
            if (abs(y) > r) continue;
            float l = length(vec2(x, y));
            if (l >= best || l > reach) continue;
            vec2 uv = vUV + vec2(x, y) * Texel;
            if (texture(MaskDist, uv).r > 0.0) {
                best = l;
                bestUv = uv;
            }
        }
    }
    if (best > 1e8) discard;

    // walls in front of the shape hide its outline too
    float shape = texture(MaskDist, bestUv).r;
    if (sceneDist(vUV) < shape - max(0.08, shape * 0.02)) discard;

    vec4 c = texture(MaskColor, bestUv);
    float cover = clamp(reach - best, 0.0, 1.0);
    FragColor = vec4(c.rgb, c.a * cover);
}
