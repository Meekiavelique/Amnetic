#before void main() {
layout(location=8) in vec4 AmneticTint;
layout(location=9) in vec2 AmneticLight;
layout(location=2) out vec4 AmneticMaterial;
layout(location=4) out vec4 AmneticAlbedo;
#before color *= v_Color;
AmneticAlbedo = vec4(color.rgb * AmneticTint.rgb, 1.0);
AmneticMaterial = vec4(.9, 0, 0, (floor(AmneticLight.x + .5) * 16.0 + floor(AmneticLight.y + .5)) / 255.0);
#ifdef AMNETIC_BLENDED
if (color.a < 0.9) { AmneticAlbedo = vec4(0); AmneticMaterial = vec4(0); }
#endif
