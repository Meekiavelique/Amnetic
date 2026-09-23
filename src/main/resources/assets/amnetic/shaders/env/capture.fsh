#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform vec3 Forward;
uniform vec3 Right;
uniform vec3 Up;
uniform vec3 SunDir;

uniform sampler2D Panorama;
uniform int HasPanorama;
uniform mat4 Turn;
uniform float PanoramaLod;

const float PI = 3.14159265359;

vec3 skyEnv(vec3 d) {
    float up = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 zenith = vec3(0.30, 0.50, 0.92);
    vec3 horizon = vec3(0.78, 0.86, 1.00);
    vec3 ground = vec3(0.34, 0.31, 0.27);
    vec3 sky = mix(horizon, zenith, pow(up, 0.55));
    return d.y >= 0.0 ? sky : mix(horizon, ground, clamp(-d.y * 2.5, 0.0, 1.0));
}

vec3 panorama(vec3 d) {
    d = normalize(mat3(Turn) * d);
    vec2 uv = vec2(0.5 + atan(d.x, -d.z) / (2.0 * PI), acos(clamp(d.y, -1.0, 1.0)) / PI);
    return textureLod(Panorama, uv, PanoramaLod).rgb;
}

void main() {
    vec2 c = vUV * 2.0 - 1.0;
    vec3 dir = normalize(Forward + Right * c.x + Up * c.y);
    if (HasPanorama == 1) {
        FragColor = vec4(panorama(dir), 1.0);
        return;
    }
    vec3 col = skyEnv(dir);
    float s = max(dot(dir, normalize(SunDir)), 0.0);
    vec3 sunColor = vec3(1.0, 0.95, 0.85);
    col += sunColor * pow(s, 2000.0) * 6.0;
    col += sunColor * pow(s, 12.0) * 0.15;
    FragColor = vec4(col, 1.0);
}
