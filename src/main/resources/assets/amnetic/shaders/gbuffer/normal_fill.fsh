#version 410 core

in vec2 vUV;

layout(location = 1) out vec4 GNormal; // xyz normal (camera-relative), a = coverage
layout(location = 2) out vec4 GMaterial; // r = roughness, g = metallic, b = materialId/255, a = unused

uniform sampler2D DepthSampler;
uniform mat4 InvViewProj;
uniform int ZeroToOne;
uniform float DefaultRoughness;

#include "amnetic:shaders/gbuffer/normal_fill.glsl"
