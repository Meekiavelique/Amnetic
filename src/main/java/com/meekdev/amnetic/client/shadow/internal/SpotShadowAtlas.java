package com.meekdev.amnetic.client.shadow.internal;

import com.meekdev.amnetic.client.shadow.ShadowSettings;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

public final class SpotShadowAtlas {

    public static final int GRID = ShadowSettings.SPOT_GRID;

    private static int textureId, colorTextureId, fboId, tileSize;

    private SpotShadowAtlas() {}

    public static int glTextureId() { return textureId; }
    public static int colorTextureId() { return colorTextureId; }
    public static int tileSize() { return tileSize; }
    public static int atlasSize() { return tileSize * GRID; }
    public static int tilePixelX(int tile) { return (tile % GRID) * tileSize; }
    public static int tilePixelY(int tile) { return (tile / GRID) * tileSize; }

    public static void ensure(int requestedTileSize) {
        if (textureId != 0 && tileSize == requestedTileSize) return;
        delete();
        tileSize = requestedTileSize;

        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        textureId = GlStateManager._genTexture();
        GlStateManager._bindTexture(textureId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F,
                atlasSize(), atlasSize(), 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);

        colorTextureId = GlStateManager._genTexture();
        GlStateManager._bindTexture(colorTextureId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
                atlasSize(), atlasSize(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        fboId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, textureId, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTextureId, 0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL11.GL_NONE);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("SpotShadowAtlas FBO incomplete: 0x" + Integer.toHexString(status));
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GlStateManager._bindTexture(prevTex);
    }

    public static void bindFbo() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
    }

    public static void delete() {
        if (textureId != 0) { GL11.glDeleteTextures(textureId); textureId = 0; }
        if (colorTextureId != 0) { GL11.glDeleteTextures(colorTextureId); colorTextureId = 0; }
        if (fboId != 0) { GL30.glDeleteFramebuffers(fboId); fboId = 0; }
        tileSize = 0;
    }
}
