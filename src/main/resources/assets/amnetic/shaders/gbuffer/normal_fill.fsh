#version 410 core

in vec2 vUV;

layout(location = 1) out vec4 GNormal;
layout(location = 2) out vec4 GMaterial;

uniform sampler2D DepthSampler;
uniform mat4 InvViewProj;
uniform int ZeroToOne;
uniform float DefaultRoughness;

#include "amnetic:shaders/gbuffer/normal_fill.glsl"
