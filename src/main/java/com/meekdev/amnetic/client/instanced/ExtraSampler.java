package com.meekdev.amnetic.client.instanced;

import java.util.function.IntSupplier;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

public record ExtraSampler(String uniformName, @Nullable Identifier textureId, int unit,
                           boolean fileBacked, @Nullable IntSupplier glTexture, int target) {

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
