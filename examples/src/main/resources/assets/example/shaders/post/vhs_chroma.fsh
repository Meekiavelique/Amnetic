#version 150

uniform sampler2D DiffuseSampler;
in vec2 texCoord;
out vec4 fragColor;

const vec3 W = vec3(0.2126, 0.7152, 0.0722);
float lum(vec3 c) { return dot(c, W); }

vec3 clipColor(vec3 c) {
    float l = lum(c);
    float n = min(min(c.r, c.g), c.b);
    float x = max(max(c.r, c.g), c.b);
    if (n < 0.0) c = l + ((c - l) * l) / (l - n);
    if (x > 1.0) c = l + ((c - l) * (1.0 - l)) / (x - l);
    return c;
}
vec3 setLum(vec3 c, float l) { c += (l - lum(c)); return clipColor(c); }

vec4 shrink(vec2 fc, vec2 res, float ratio) {
    float bandWidth = 1.0 / ratio;
    float t = mod(fc.x, bandWidth) / bandWidth;
    fc.x = floor(fc.x * ratio) / ratio;
    vec2 uv = fc / res;
    vec4 a = texture(DiffuseSampler, uv);
    uv.x += bandWidth / res.x;
    vec4 b = texture(DiffuseSampler, uv);
    return mix(a, b, t);
}

void main() {
    vec2 res = vec2(textureSize(DiffuseSampler, 0));
    vec2 fc = texCoord * res;

    vec4 luma = shrink(fc, res, 0.5);
    luma.rgb = setLum(vec3(0.5), lum(luma.rgb));

    vec4 chroma = shrink(fc, res, 1.0 / 14.0);
    vec3 outc = setLum(chroma.rgb, lum(luma.rgb));

    fragColor = vec4(outc, 1.0);
}
