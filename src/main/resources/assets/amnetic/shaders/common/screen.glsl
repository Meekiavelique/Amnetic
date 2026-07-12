vec3 reconstruct(vec2 uv, float depth) {
    float z = (ZeroToOne == 1) ? depth : depth * 2.0 - 1.0;
    vec4 clip = vec4(uv * 2.0 - 1.0, z, 1.0);
    vec4 p = InvViewProj * clip;
    return p.xyz / p.w;
}

// picks the neighbour with the smaller depth jump for the finite-difference derivative. if both
// neighbours jump past a relative threshold the uv sits on a silhouette with disocclusion on both
// sides and either derivative would extrapolate across it, so return zero and let betterNormal
// fall back
vec3 betterAxis(vec2 uv, vec3 P, vec2 offset, float dNeg, float dPos, float c) {
    float threshold = max(c * 0.01, 1e-5);
    bool negBad = abs(dNeg - c) > threshold;
    bool posBad = abs(dPos - c) > threshold;
    if (negBad && posBad) return vec3(0.0);
    if (negBad) return reconstruct(uv + offset, dPos) - P;
    if (posBad) return P - reconstruct(uv - offset, dNeg);
    return (abs(dNeg - c) < abs(dPos - c)) ? (P - reconstruct(uv - offset, dNeg))
                                           : (reconstruct(uv + offset, dPos) - P);
}

vec3 betterNormal(vec2 uv, vec3 P) {
    vec2 ts = 1.0 / vec2(textureSize(DepthSampler, 0));
    float c = texture(DepthSampler, uv).r;
    float dl = texture(DepthSampler, uv - vec2(ts.x, 0.0)).r;
    float dr = texture(DepthSampler, uv + vec2(ts.x, 0.0)).r;
    float dd = texture(DepthSampler, uv - vec2(0.0, ts.y)).r;
    float du = texture(DepthSampler, uv + vec2(0.0, ts.y)).r;
    vec3 Px = betterAxis(uv, P, vec2(ts.x, 0.0), dl, dr, c);
    vec3 Py = betterAxis(uv, P, vec2(0.0, ts.y), dd, du, c);
    vec3 n;
    if (dot(Px, Px) < 1e-16 || dot(Py, Py) < 1e-16) {
        n = normalize(-P);
    } else {
        n = cross(Px, Py);
        float l = length(n);
        n = (l > 1e-8) ? n / l : normalize(-P);
    }
    if (dot(n, P) > 0.0) n = -n;
    return n;
}
