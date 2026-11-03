#version 330 core

out vec4 FragColor;

uniform sampler2D DepthSampler;
uniform sampler2D DecalSampler;
uniform int HasTexture;
uniform mat4 InvViewProj;
uniform mat4 DecalInv; // camera-relative world -> decal local (box is [-0.5, 0.5])
uniform int ZeroToOne;
uniform float Opacity;
uniform float AngleFade;
uniform vec3 DecalAxis; // projection axis (camera-relative world)
uniform vec3 Tint;
uniform int Multiply; // 1 = multiply onto the surface (blend dst_color, zero), keeps its lighting
uniform int FlipV; // 1 = image top row at the top of the box
uniform int TppLitWater;
uniform int TppWorldLit;
uniform sampler2D TppLightmap;
uniform vec2 TppLightUV;

#include "amnetic:shaders/common/screen.glsl"

vec3 normalFromDepth(vec2 uv, vec3 P) {
    vec2 ts = 1.0 / vec2(textureSize(DepthSampler, 0));
    vec3 px = reconstruct(uv + vec2(ts.x, 0.0), texture(DepthSampler, uv + vec2(ts.x, 0.0)).r) - P;
    vec3 py = reconstruct(uv + vec2(0.0, ts.y), texture(DepthSampler, uv + vec2(0.0, ts.y)).r) - P;
    vec3 n = cross(px, py);
    float l = length(n);
    n = (l > 1e-8) ? n / l : vec3(0.0, 1.0, 0.0);
    if (dot(n, P) > 0.0) n = -n;
    return n;
}

void main() {
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(DepthSampler, 0));
    float depth = texture(DepthSampler, uv).r;
    if (IS_SKY(depth)) discard; // sky

    vec3 P = reconstruct(uv, depth);
    vec3 local = (DecalInv * vec4(P, 1.0)).xyz;
    if (abs(local.x) > 0.5 || abs(local.y) > 0.5 || abs(local.z) > 0.5) discard;

    vec2 duv = local.xz + 0.5; // project along the box's axis (local Y)
    if (FlipV == 1) duv.y = 1.0 - duv.y;

    // DecalAxis is the surface normal the decal sticks to (up for a floor), fade by how aligned the surface is with it
    // angle fade at 0 skips the depth normal and its two extra depth reads
    float fade = AngleFade > 0.0 ? smoothstep(AngleFade, 1.0, dot(normalFromDepth(uv, P), normalize(DecalAxis))) : 1.0;
    fade *= 1.0 - smoothstep(0.35, 0.5, abs(local.y));
    if (fade <= 0.0) discard;

    vec3 col;
    float a;
    if (HasTexture == 1) {
        vec4 t = texture(DecalSampler, duv);
        col = t.rgb * Tint;
        a = t.a;
    } else {
        float r = length(duv - 0.5) * 2.0; // procedural soft scorch
        a = 1.0 - smoothstep(0.55, 1.0, r);
        col = Tint;
    }

    a *= Opacity * fade;
    if (a <= 0.001) discard;
    if (Multiply == 1) {
        FragColor = vec4(mix(vec3(1.0), col, a), 1.0);
        return;
    }
    if(TppLitWater==1 || TppWorldLit==1)col*=texture(TppLightmap,TppLightUV).rgb
            * clamp((max(TppLightUV.x,TppLightUV.y)*16.0-.5),0.0,1.0);
    FragColor = vec4(col, a);
}
