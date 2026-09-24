package com.meekdev.amnetic.client.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

public final class TexelBuffer implements AutoCloseable {

    private final int buffer;
    private final int texture;

    public TexelBuffer(long sizeBytes) {
        buffer = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, sizeBytes, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
        texture = GL11.glGenTextures();
    }

    public void upload(FloatBuffer data) {
        GlStateManager._glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        GL15.glBufferSubData(GL31.GL_TEXTURE_BUFFER, 0L, data);
        GlStateManager._glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
    }

    public void bind(int unit) {
        select(unit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, buffer);
        select(0);
    }

    public static void unbind(int unit) {
        select(unit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        select(0);
    }

    private static void select(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
    }

    @Override
    public void close() {
        GL11.glDeleteTextures(texture);
        GlStateManager._glDeleteBuffers(buffer);
    }
}
