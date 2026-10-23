shader_type surface;
render_mode blend_mix;

uniform float Speed : hint_range(0.0, 4.0) = 1.0;
uniform vec4 Tint : source_color = vec4(0.3, 0.7, 1.0, 1.0);

void fragment() {
    vec2 p = UV * 6.0;
    float t = TIME * Speed;
    float v = sin(p.x + t) + sin(p.y + t * 0.7) + sin(p.x + p.y + t * 1.3) + sin(length(p - 3.0) * 1.5 - t);
    v = v * 0.25 + 0.5;
    vec3 base = mix(Tint.rgb * 0.25, Tint.rgb, v);
    base += HOVER * 0.25;
    COLOR = vec4(base, COLOR.a * Tint.a);
}
