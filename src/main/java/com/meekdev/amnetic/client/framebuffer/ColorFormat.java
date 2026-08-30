package com.meekdev.amnetic.client.framebuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public enum ColorFormat {
    RGBA8(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 4),
    RGBA16F(GL30.GL_RGBA16F, GL11.GL_RGBA, GL30.GL_HALF_FLOAT, 8),
    R11G11B10F(GL30.GL_R11F_G11F_B10F, GL11.GL_RGB, GL11.GL_FLOAT, 4),
    R8(GL30.GL_R8, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, 1),
    R16F(GL30.GL_R16F, GL11.GL_RED, GL30.GL_HALF_FLOAT, 2),
    R32F(GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, 4);

    private final int glInternalFormat;
    private final int glFormat;
    private final int glType;
    private final int pixelBytes;

    ColorFormat(int glInternalFormat, int glFormat, int glType, int pixelBytes) {
        this.glInternalFormat = glInternalFormat;
        this.glFormat = glFormat;
        this.glType = glType;
        this.pixelBytes = pixelBytes;
    }

    public int glInternalFormat() {
        return glInternalFormat;
    }

    public int glFormat() {
        return glFormat;
    }

    public int glType() {
        return glType;
    }

    public int pixelBytes() {
        return pixelBytes;
    }
}
