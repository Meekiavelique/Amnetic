#version 330 core

in vec2 vUv;
in vec4 vColor;
in vec2 vPos;
flat in vec4 vParams; // mode, radius, halfW, halfH
flat in vec2 vExtra;  // borderW, softness
flat in vec4 vClip;   // clip rect in gui px, x1 <= 0 means none

uniform sampler2D Tex;
uniform vec2 ScreenSize; // gui-scaled dims, matches vPos space

out vec4 FragColor;

// analytic rounded box, crisp at any scale without an atlas
float roundedBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    if (vClip.z > 0.0 && (vPos.x < vClip.x || vPos.y < vClip.y || vPos.x > vClip.z || vPos.y > vClip.w)) discard;

    int mode = int(vParams.x + 0.5);

    if (mode == 1) { // sdf glyph, screen-space aa keeps the edge ~1px at every size
        // vParams.y is edge offset in sdf units (positive grows the glyph: outlines),
        // vParams.z is softness (wide transition band: glow/blur), both compose freely
        float d = texture(Tex, vUv).r;
        float aa = fwidth(d);
        float off = vParams.y;
        float soft = vParams.z;
        float cov = smoothstep(0.5 - off - aa - soft, 0.5 - off + aa + soft, d);
        if (cov <= 0.0) discard;
        FragColor = vec4(vColor.rgb, vColor.a * cov);
        return;
    }

    if (mode == 2) { // plain textured
        FragColor = texture(Tex, vUv) * vColor;
        return;
    }

    if (mode == 3) { // frosted glass, Tex is the scene capture, extra.y is blur radius
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
        // tint multiplies, alpha mixes tint over the blurred scene
        vec3 tinted = mix(acc, acc * vColor.rgb, vColor.a);
        FragColor = vec4(tinted, cov3);
        return;
    }

    // rect sdf, uv is the pixel offset from the rect center
    float d = roundedBox(vUv, vParams.zw, vParams.y);
    float border = vExtra.x;
    float softness = vExtra.y;
    float aa = max(fwidth(d), 1e-4);

    float cov;
    if (softness > 0.0) {
        cov = 1.0 - smoothstep(-softness, softness, d); // shadow feather
    } else if (border > 0.0) {
        float outer = 1.0 - smoothstep(-aa, aa, d);
        float inner = 1.0 - smoothstep(-aa, aa, d + border);
        cov = outer - inner; // ring
    } else {
        cov = 1.0 - smoothstep(-aa, aa, d);
    }
    if (cov <= 0.0) discard;
    FragColor = vec4(vColor.rgb, vColor.a * cov);
}
