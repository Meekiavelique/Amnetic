#version 330

//   texel0 = colour.rgb, detail (a)
//   texel1 = fade (r)
uniform sampler2D Sampler0;        // entity skin
uniform sampler2D UniformSampler;

in vec2 vUV;

out vec4 fragColor;

void main() {
    vec4 skin = texture(Sampler0, vUV);
    if (skin.a < 0.1) discard;

    vec4 c0 = texelFetch(UniformSampler, ivec2(0, 0), 0);
    float fade = texelFetch(UniformSampler, ivec2(1, 0), 0).r;
    vec3 paint = c0.rgb;
    float detail = c0.a;

    float lum = dot(skin.rgb, vec3(0.299, 0.587, 0.114));
    float factor = mix(1.0, 0.35 + 0.9 * lum, detail);   // flat vs shaded
    fragColor = vec4(paint * factor, skin.a * fade);
}
