package com.meekdev.amnetic.client.instanced;

import java.util.function.IntSupplier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record ExtraSampler(String uniformName, @Nullable ResourceLocation textureId, int unit,
                           boolean fileBacked, @Nullable IntSupplier glTexture, boolean cube) {
    public ExtraSampler(String uniformName, ResourceLocation textureId, int unit) {
        this(uniformName, textureId, unit, false, null, false);
    }

    public ExtraSampler(String uniformName, ResourceLocation textureId, int unit, boolean fileBacked) {
        this(uniformName, textureId, unit, fileBacked, null, false);
    }

    public ExtraSampler(String uniformName, IntSupplier glTexture, int unit) {
        this(uniformName, null, unit, false, glTexture, false);
    }

    public ExtraSampler(String uniformName, IntSupplier glTexture, int unit, boolean cube) {
        this(uniformName, null, unit, false, glTexture, cube);
    }
}
