float linearizeDepth(float depth, float near, float far, bool zeroToOne) {
    if (zeroToOne) {
        return (near * far) / (far - depth * (far - near));
    }
    float ndc = depth * 2.0 - 1.0;
    return (2.0 * near * far) / (far + near - ndc * (far - near));
}

bool isOccluded(float sceneDepth, float fragDepth, float near, float far, bool zeroToOne, float bias) {
    float scene = linearizeDepth(sceneDepth, near, far, zeroToOne);
    float frag = linearizeDepth(fragDepth, near, far, zeroToOne);
    return frag > scene + bias * frag;
}

float occlusionFactor(float sceneDepth, float fragDepth, float near, float far, bool zeroToOne, float fadeDepth) {
    float scene = linearizeDepth(sceneDepth, near, far, zeroToOne);
    float frag = linearizeDepth(fragDepth, near, far, zeroToOne);
    return clamp((frag - scene) / max(fadeDepth, 1e-4), 0.0, 1.0);
}
