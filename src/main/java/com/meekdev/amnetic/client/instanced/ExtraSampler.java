package com.meekdev.amnetic.client.instanced;

import java.util.function.IntSupplier;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

// a texture bound alongside the mesh's own, either one the texture manager knows by name or one
// the caller resolves itself
//
// not everything worth sampling is in the texture manager. minecraft's own light map is a gpu
// texture the game renderer owns and never registers, so a mod that wants the light a block would
// get has no identifier to ask for. a supplier of the raw gl name reaches those
public record ExtraSampler(String uniformName, @Nullable Identifier textureId, int unit,
                           boolean fileBacked, @Nullable IntSupplier glTexture) {

    public ExtraSampler(String uniformName, Identifier textureId, int unit) {
        this(uniformName, textureId, unit, false, null);
    }

    public ExtraSampler(String uniformName, Identifier textureId, int unit, boolean fileBacked) {
        this(uniformName, textureId, unit, fileBacked, null);
    }

    public ExtraSampler(String uniformName, IntSupplier glTexture, int unit) {
        this(uniformName, null, unit, false, glTexture);
    }
}
