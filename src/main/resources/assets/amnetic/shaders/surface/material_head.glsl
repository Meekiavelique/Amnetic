#version 330 core
in vec2 vUv;
in vec4 vColor;
in vec2 vPos;
flat in vec4 vParams;
flat in vec2 vExtra;
flat in vec4 vClip;
uniform sampler2D Tex;
uniform float Time;
uniform float Hover;
uniform float Pressed;
uniform float Focus;
uniform vec2 ScreenSize;
out vec4 FragColor;

#define PI 3.14159265359
vec2 UV;
float TIME;
float HOVER;
float PRESSED;
float FOCUS;
vec2 RECT_SIZE;
vec2 SCREEN_UV;
vec4 COLOR;
vec4 TEXTURE_at(vec2 uv) { return texture(Tex, uv); }

float amnetic_roundedBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
