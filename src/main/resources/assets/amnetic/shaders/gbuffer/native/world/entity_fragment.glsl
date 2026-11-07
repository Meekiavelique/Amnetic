#after color *= faceVertexColor * ColorModulator;
AmneticAlbedo = vec4(color.rgb, 1);
AmneticMaterial = vec4(.85, 0, 0, 0);
#ifdef AMNETIC_BLENDED
if (color.a < 0.9) { AmneticAlbedo = vec4(0); AmneticMaterial = vec4(0); }
#endif
#before void main() {
layout(location=2) out vec4 AmneticMaterial;
layout(location=4) out vec4 AmneticAlbedo;
