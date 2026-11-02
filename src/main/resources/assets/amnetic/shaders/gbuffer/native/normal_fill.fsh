#version 450

layout(location=0) in vec2 vUV;

layout(location = 1) out vec4 GNormal;
layout(location = 2) out vec4 GMaterial;

uniform sampler2D DepthSampler;
layout(std140) uniform ReconstructParams {
    mat4 InvViewProj;
    int ZeroToOne;
    float DefaultRoughness;
};

#include "amnetic:shaders/gbuffer/normal_fill.glsl"
