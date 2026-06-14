package com.meekdev.amnetic.client.instanced;

import net.minecraft.resources.Identifier;

public record ExtraSampler(String uniformName, Identifier textureId, int unit, boolean fileBacked) {

    public ExtraSampler(String uniformName, Identifier textureId, int unit) {
        this(uniformName, textureId, unit, false);
    }
}
