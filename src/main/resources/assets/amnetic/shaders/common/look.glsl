vec3 lookAces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

vec3 lookDisplay(vec3 hdr) {
    return pow(lookAces(max(hdr, 0.0)), vec3(1.0 / 2.2));
}

float lookLuma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float lookShare(vec3 without, vec3 all) {
    return clamp(1.0 - lookLuma(lookDisplay(without)) / max(lookLuma(lookDisplay(all)), 1e-4), 0.0, 1.0);
}
