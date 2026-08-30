#version 330 core

in vec2 vUv;
in vec4 vColor;
in vec2 vPos;
flat in vec4 vParams;
flat in vec2 vExtra;
flat in vec4 vClip;

uniform sampler2D Tex;
uniform vec2 ScreenSize;

out vec4 FragColor;

float roundedBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    if (vClip.z > 0.0 && (vPos.x < vClip.x || vPos.y < vClip.y || vPos.x > vClip.z || vPos.y > vClip.w)) discard;

    int mode = int(vParams.x + 0.5);

    if (mode == 1) {
        float d = texture(Tex, vUv).r;
        float aa = fwidth(d);
        float off = vParams.y;
        float soft = vParams.z;
        float cov = smoothstep(0.5 - off - aa - soft, 0.5 - off + aa + soft, d);
        if (cov <= 0.0) discard;
        FragColor = vec4(vColor.rgb, vColor.a * cov);
        return;
    }

    if (mode == 2) {
        FragColor = texture(Tex, vUv) * vColor;
        return;
    }

    if (mode == 3) {
        vec2 suv = vec2(vPos.x / ScreenSize.x, 1.0 - vPos.y / ScreenSize.y);
        vec2 px = vExtra.y / vec2(textureSize(Tex, 0));
        vec3 acc = vec3(0.0);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                acc += texture(Tex, suv + vec2(i, j) * px).rgb;
            }
        }
        acc /= 9.0;
        float d3 = roundedBox(vUv, vParams.zw, vParams.y);
        float aa3 = max(fwidth(d3), 1e-4);
        float cov3 = 1.0 - smoothstep(-aa3, aa3, d3);
        if (cov3 <= 0.0) discard;
        vec3 tinted = mix(acc, acc * vColor.rgb, vColor.a);
        FragColor = vec4(tinted, cov3);
        return;
    }

    float d = roundedBox(vUv, vParams.zw, vParams.y);
    float border = vExtra.x;
    float softness = vExtra.y;
    float aa = max(fwidth(d), 1e-4);

    float cov;
    if (softness > 0.0) {
        cov = 1.0 - smoothstep(-softness, softness, d);
    } else if (border > 0.0) {
        float outer = 1.0 - smoothstep(-aa, aa, d);
        float inner = 1.0 - smoothstep(-aa, aa, d + border);
        cov = outer - inner;
    } else {
        cov = 1.0 - smoothstep(-aa, aa, d);
    }
    if (cov <= 0.0) discard;
    FragColor = vec4(vColor.rgb, vColor.a * cov);
}
