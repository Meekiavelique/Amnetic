void main() {
    if (vClip.z > 0.0 && (vPos.x < vClip.x || vPos.y < vClip.y || vPos.x > vClip.z || vPos.y > vClip.w)) discard;
    UV = vUv / max(vParams.zw * 2.0, vec2(1e-4)) + 0.5;
    TIME = Time;
    HOVER = Hover;
    PRESSED = Pressed;
    FOCUS = Focus;
    RECT_SIZE = vParams.zw * 2.0;
    SCREEN_UV = gl_FragCoord.xy / ScreenSize;
    COLOR = vColor;
    fragment();
    float d = amnetic_roundedBox(vUv, vParams.zw, vParams.y);
    float aa = max(fwidth(d), 1e-4);
    float cov = 1.0 - smoothstep(-aa, aa, d);
    if (cov <= 0.0) discard;
    FragColor = vec4(COLOR.rgb, COLOR.a * cov);
}
