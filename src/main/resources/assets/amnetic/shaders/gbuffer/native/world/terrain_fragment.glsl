#replace ) * vertexColor;
);
AmneticAlbedo = vec4(color.rgb * AmneticTint.rgb, 1);
AmneticMaterial = vec4(.9, 0, 0, (floor(AmneticLight.x + .5) * 16.0 + floor(AmneticLight.y + .5)) / 255.0);
color *= vertexColor;
#before void main() {
layout(location=2) out vec4 AmneticMaterial;
layout(location=4) out vec4 AmneticAlbedo;
layout(location=8) in vec4 AmneticTint;
layout(location=9) in vec2 AmneticLight;
