package com.meekdev.amnetic.client.instanced;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

// a texture bound alongside the mesh's own, either one the texture manager knows by name or one
// the caller resolves itself
//
// not everything worth sampling is in the texture manager. minecraft's own light map is a gpu
// texture the game renderer owns and never registers, so a mod that wants the light a block would
// get has no identifier to ask for. a supplier of the raw gl name reaches those
public record ExtraSampler(String uniformName, @Nullable Identifier textureId, int unit,
                           boolean fileBacked, @Nullable IntSupplier glTexture, int target
                           //? if >=26.2 {
                           , @Nullable Supplier<com.mojang.renderpearl.api.textures.GpuTextureView> nativeTexture
                           //?}
                           ) {

    //? if >=26.2 {
    public ExtraSampler(String uniformName, Identifier textureId, int unit,
                        boolean fileBacked, IntSupplier glTexture, int target) {
        this(uniformName, textureId, unit, fileBacked, glTexture, target, null);
    }

    public ExtraSampler(String uniformName,
                        Supplier<com.mojang.renderpearl.api.textures.GpuTextureView> texture, int unit) {
        this(uniformName, null, unit, false, null, GL11.GL_TEXTURE_2D, Objects.requireNonNull(texture));
    }
    //?}

    public ExtraSampler(String uniformName, Identifier textureId, int unit) {
        this(uniformName, textureId, unit, false, null, GL11.GL_TEXTURE_2D);
    }

    public ExtraSampler(String uniformName, Identifier textureId, int unit, boolean fileBacked) {
        this(uniformName, textureId, unit, fileBacked, null, GL11.GL_TEXTURE_2D);
    }

    public ExtraSampler(String uniformName, IntSupplier glTexture, int unit) {
        this(uniformName, null, unit, false, glTexture, GL11.GL_TEXTURE_2D);
    }

    public ExtraSampler(String uniformName, IntSupplier glTexture, int unit, boolean cube) {
        this(uniformName, null, unit, false, glTexture, cube ? GL13.GL_TEXTURE_CUBE_MAP : GL11.GL_TEXTURE_2D);
    }

    public ExtraSampler(String uniformName, IntSupplier glTexture, int unit, int target) {
        this(uniformName, null, unit, false, glTexture, target);
    }

    public boolean cube() {
        return target == GL13.GL_TEXTURE_CUBE_MAP;
    }

    public boolean volume() {
        return target == GL12.GL_TEXTURE_3D;
    }
}
