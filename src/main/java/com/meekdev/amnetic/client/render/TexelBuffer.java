package com.meekdev.amnetic.client.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

public final class TexelBuffer implements AutoCloseable {

    private final int buffer;
    private final int texture;
    private final FloatBuffer lastUpload;
    private boolean uploaded;

    public TexelBuffer(long sizeBytes) {
        lastUpload = BufferUtils.createFloatBuffer(Math.toIntExact(sizeBytes / Float.BYTES));
        buffer = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, sizeBytes, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
        texture = GL11.glGenTextures();
        select(0);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, buffer);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
    }

    public void upload(FloatBuffer data) {
        // Static lights and materials often stay unchanged for many frames. Avoid
        // writing storage the GPU may still be reading when there is no new data.
        if (uploaded && lastUpload.equals(data)) return;
        GlStateManager._glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        GL15.glBufferSubData(GL31.GL_TEXTURE_BUFFER, 0L, data);
        GlStateManager._glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
        lastUpload.clear();
        lastUpload.put(data.duplicate()).flip();
        uploaded = true;
    }

    public void bind(int unit) {
        select(unit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
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
