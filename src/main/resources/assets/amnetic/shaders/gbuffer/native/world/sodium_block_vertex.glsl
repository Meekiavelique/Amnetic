#before void main() {
layout(location=8) out vec4 AmneticTint;
layout(location=9) out vec2 AmneticLight;
#before v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);
AmneticTint = _vert_color;
AmneticLight = clamp((_vert_tex_light_coord * 256.0 - 8.0) / 16.0, 0.0, 15.0);
