package com.meekdev.amnetic.client.particle;

import net.minecraft.resources.ResourceLocation;

public enum BillboardMode {
    SPHERICAL(new ResourceLocation("amnetic", "particle/billboard")),
    VELOCITY_STRETCHED(new ResourceLocation("amnetic", "particle/billboard_stretched")),
    BEAM(new ResourceLocation("amnetic", "particle/billboard_beam"));

    private final ResourceLocation vertexShader;

    BillboardMode(ResourceLocation vertexShader) { this.vertexShader = vertexShader; }

    public ResourceLocation vertexShader() { return vertexShader; }
}
