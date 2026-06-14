#version 330

#moj_import <minecraft:globals.glsl>   // ScreenSize, GameTime

uniform sampler2D Sampler0;        // entity skin
uniform sampler2D SceneColorSampler;
uniform sampler2D UniformSampler;

in vec3 vViewPos;
in vec3 vViewNormal;
in vec4 vColor;
in vec2 vUV;
in float vModelY;

out vec4 fragColor;

float hash(float n) { return fract(sin(n) * 43758.5453); }

void main() {
    vec4 c0 = texelFetch(UniformSampler, ivec2(0, 0), 0);
    vec4 c1 = texelFetch(UniformSampler, ivec2(1, 0), 0);
    float fade = texelFetch(UniformSampler, ivec2(2, 0), 0).r;

    vec3 holo = c0.rgb;
    float baseAlpha = c0.a;
    float scanDensity = c1.r * 256.0;
    float scanSpeed = c1.g * 8.0;
    float glitch = c1.b;
    float flickerAmt = c1.a;

    float t = GameTime * 1000.0;   // GameTime is fractional; scale for visible motion

    vec4 skin = texture(Sampler0, vUV);
    if (skin.a < 0.1) discard;
    float lum = dot(skin.rgb, vec3(0.299, 0.587, 0.114));
    vec3 figure = holo * (0.35 + 0.65 * lum);

    float scan = 0.6 + 0.4 * sin((vModelY * scanDensity) - t * scanSpeed * 6.2831);

    vec3 N = normalize(vViewNormal);
    vec3 V = normalize(-vViewPos);
    float fresnel = pow(1.0 - clamp(dot(N, V), 0.0, 1.0), 2.0);

    float flicker = 1.0 - flickerAmt * (0.5 + 0.5 * sin(t * 37.0)) * step(0.5, hash(floor(t * 8.0)));

    vec3 color = figure * scan * flicker + holo * fresnel * 0.8;
    float alpha = clamp(baseAlpha * scan + fresnel * 0.4, 0.0, 1.0) * flicker * fade;

    if (glitch > 0.001) {
        vec2 uv = gl_FragCoord.xy / ScreenSize;
        float band = floor(uv.y * 40.0);
        float jitter = (hash(band + floor(t * 10.0)) - 0.5) * glitch * 0.05;
        float split = glitch * 0.01;
        vec3 rgbSplit = vec3(
            texture(SceneColorSampler, uv + vec2(jitter + split, 0.0)).r,
            texture(SceneColorSampler, uv + vec2(jitter, 0.0)).g,
            texture(SceneColorSampler, uv + vec2(jitter - split, 0.0)).b);
        color = mix(color, color + rgbSplit * holo, glitch);
        alpha = clamp(alpha + glitch * 0.15 * step(0.7, hash(band)), 0.0, 1.0);
    }

    fragColor = vec4(color, alpha);
}
