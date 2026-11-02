#before void main() {
layout(location=8) out vec4 AmneticTint;
layout(location=9) out vec2 AmneticLight;
#after void main() {
AmneticTint = Color;
AmneticLight = vec2(UV2) / 16.0;
