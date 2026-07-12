#version 430 core


in vec2 vUV;
out vec4 FragColor;

uniform sampler2D Scatter; // half-res: rgb = scatter, a = scene depth
uniform sampler2D DepthSampler; // full-res scene depth

void main() {
    float d = texture(DepthSampler, vUV).r;
    vec2 ts = 1.0 / vec2(textureSize(Scatter, 0));

    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 uv = vUV + vec2(float(x), float(y)) * ts;
            vec4 s = texture(Scatter, uv);
            float dz = abs(s.a - d);
            float w = exp(-dz * 512.0) * exp(-float(x * x + y * y) * 0.5);
            sum += s.rgb * w;
            wsum += w;
        }
    }
    vec3 col = wsum > 1e-5 ? sum / wsum : texture(Scatter, vUV).rgb;
    FragColor = vec4(max(col, vec3(0.0)), 1.0);
}
