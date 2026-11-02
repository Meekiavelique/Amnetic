#after void main() {
AmneticAlbedo = vec4(0);
AmneticMaterial = vec4(0);
#before void main() {
layout(location=2) out vec4 AmneticMaterial;
layout(location=4) out vec4 AmneticAlbedo;
