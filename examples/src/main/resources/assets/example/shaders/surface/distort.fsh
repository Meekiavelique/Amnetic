shader_type surface;

uniform float height : hint_range(0.0, 0.5, 0.01) = 0.30;
uniform float width : hint_range(0.0, 0.5, 0.01) = 0.42;
uniform float radius : hint_range(0.0, 0.5, 0.01) = 0.10;
uniform float speed : hint_range(0.0, 1.0, 0.01) = 0.06;
uniform float edge_source : hint_range(0.0, 1.0, 0.01) = 1.0;
uniform vec4 edge_color : source_color = vec4(0.45, 0.85, 1.0, 1.0);
uniform vec4 inner_color : source_color = vec4(0.05, 0.10, 0.18, 0.92);

uniform sampler2D noise;

float sd_round_rect(vec2 p, vec2 s, float r) {
    vec2 q = abs(p) - s + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void fragment() {
    vec2 uv = UV;
    float noise_r = texture(noise, fract(TIME * speed + uv)).r * edge_source;
    float d = sd_round_rect(uv - 0.5, vec2(width, height), radius);

    float m = step(d - noise_r * 0.1, 0.0);
    float m2 = step(d - noise_r * 0.1 - 0.05, 0.0);
    float m3 = m2 - m;

    vec4 edge = m3 * edge_color;
    vec4 inner = m * inner_color;
    COLOR = mix(edge, inner, inner.a);
}
