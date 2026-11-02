#after color *= faceVertexColor * ColorModulator;
AmneticAlbedo = vec4(color.rgb, 1);
AmneticMaterial = vec4(.85, 0, 0, 0);
#before void main() {
layout(location=2) out vec4 AmneticMaterial;
layout(location=4) out vec4 AmneticAlbedo;
