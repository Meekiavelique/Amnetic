#version 150

uniform sampler2D DiffuseSampler;
in vec2 texCoord;
out vec4 fragColor;

const vec3 W = vec3(0.2126, 0.7152, 0.0722);
float lum(vec3 c) { return dot(c, W); }

vec3 hblur(vec2 uv, vec2 res) {
    float s = 2.0 / res.x;
    vec3 sum = vec3(0.0);
    for (int i = -4; i <= 4; i++) sum += texture(DiffuseSampler, uv + vec2(float(i) * s, 0.0)).rgb;
    return sum / 9.0;
}

void main() {
    vec2 res = vec2(textureSize(DiffuseSampler, 0));
    vec3 pixel = texture(DiffuseSampler, texCoord).rgb;
    vec3 blur = hblur(texCoord, res);

    pixel = pixel + (pixel - blur) * 1.6;

    pixel = clamp(pixel, 0.0, 1.0);
    pixel = pixel * 1.03 - 0.012;

    pixel = mix(vec3(lum(pixel)), pixel, 0.86);
    pixel *= vec3(1.02, 1.0, 0.97);

    fragColor = vec4(clamp(pixel, 0.0, 1.0), 1.0);
}
