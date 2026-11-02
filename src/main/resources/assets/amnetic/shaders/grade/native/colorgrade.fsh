#version 450

layout(location=0) in vec2 vUV;
layout(location=0) out vec4 FragColor;

layout(std140) uniform GradeParams {
    int HasLut;
    float LutSize;
    float LutIntensity;
    float Exposure;
    float Contrast;
    float Saturation;
    float Brightness;
    float Temperature;
    float Tint;
    float Gamma;
};
uniform sampler2D ColorSampler;
uniform sampler2D LutSampler;

#include "amnetic:shaders/grade/colorgrade.glsl"
