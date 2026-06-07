package com.meekdev.amnetic.client.particle;

import net.minecraft.resources.Identifier;

public enum BillboardMode {
    SPHERICAL(Identifier.fromNamespaceAndPath("amnetic", "particle/billboard")),
    VELOCITY_STRETCHED(Identifier.fromNamespaceAndPath("amnetic", "particle/billboard_stretched"));

    private final Identifier vertexShader;

    BillboardMode(Identifier vertexShader) { this.vertexShader = vertexShader; }

    public Identifier vertexShader() { return vertexShader; }
}
