#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D ColorSampler;
uniform sampler2D LutSampler;
uniform int HasLut;
uniform float LutSize;
uniform float LutIntensity;

uniform float Exposure;
uniform float Contrast;
uniform float Saturation;
uniform float Brightness;
uniform float Temperature;
uniform float Tint;
uniform float Gamma;

#include "amnetic:shaders/grade/colorgrade.glsl"
