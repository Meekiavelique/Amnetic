#version 330 core

in vec2 vUv;
in vec4 vColor;
flat in vec4 vParams; // mode, radius, halfW, halfH
flat in vec2 vExtra;  // borderW, softness

uniform sampler2D Tex;

out vec4 FragColor;

// analytic rounded box, crisp at any scale without an atlas
float roundedBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    int mode = int(vParams.x + 0.5);

    if (mode == 1) { // sdf glyph, screen-space aa keeps the edge ~1px at every size
        float d = texture(Tex, vUv).r;
        float aa = fwidth(d);
        float cov = smoothstep(0.5 - aa, 0.5 + aa, d);
        if (cov <= 0.0) discard;
        FragColor = vec4(vColor.rgb, vColor.a * cov);
        return;
    }

    if (mode == 2) { // plain textured
        FragColor = texture(Tex, vUv) * vColor;
        return;
    }

    // rect sdf, uv is the pixel offset from the rect center
    float d = roundedBox(vUv, vParams.zw, vParams.y);
    float border = vExtra.x;
    float softness = vExtra.y;
    float aa = max(fwidth(d), 1e-4);

    float cov;
    if (softness > 0.0) {
        // soft shadow, feather outward from the edge
        cov = 1.0 - smoothstep(-softness, softness, d);
    } else if (border > 0.0) {
        // ring: outside coverage minus inside coverage
        float outer = 1.0 - smoothstep(-aa, aa, d);
        float inner = 1.0 - smoothstep(-aa, aa, d + border);
        cov = outer - inner;
    } else {
        cov = 1.0 - smoothstep(-aa, aa, d);
    }
    if (cov <= 0.0) discard;
    FragColor = vec4(vColor.rgb, vColor.a * cov);
}
