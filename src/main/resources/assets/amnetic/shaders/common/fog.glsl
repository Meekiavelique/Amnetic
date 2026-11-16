// the host's distance fog, left unset the strength is 0 and nothing is fogged
uniform vec4 FogTint;
uniform vec4 FogRange;

float fogAmount(vec3 fromCamera) {
    float spherical = length(fromCamera);
    float cylindrical = max(length(fromCamera.xz), abs(fromCamera.y));
    float environmental = clamp((spherical - FogRange.x) / max(FogRange.y - FogRange.x, 1e-4), 0.0, 1.0);
    float renderDistance = clamp((cylindrical - FogRange.z) / max(FogRange.w - FogRange.z, 1e-4), 0.0, 1.0);
    return max(environmental, renderDistance) * FogTint.a;
}

vec3 worldFog(vec3 color, vec3 fromCamera) {
    return mix(color, FogTint.rgb, fogAmount(fromCamera));
}
