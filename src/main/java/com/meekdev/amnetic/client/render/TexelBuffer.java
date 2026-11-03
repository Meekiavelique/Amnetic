package com.meekdev.amnetic.client.render;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
//? if >=26.2 {
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
//?}

public final class TexelBuffer implements AutoCloseable {

    private final int buffer;
    private final int texture;
    private final FloatBuffer lastUpload;
    private boolean uploaded;
    //? if >=26.2 {
    private final com.mojang.renderpearl.api.buffers.GpuBuffer nativeBuffer;
    private ByteBuffer uploadBytes;
    public GpuBufferSlice nativeSlice() { return nativeBuffer.slice(); }
    //?}

    public TexelBuffer(long sizeBytes) {
        lastUpload = BufferUtils.createFloatBuffer(Math.toIntExact(sizeBytes / Float.BYTES));
        //? if >=26.2 {
        buffer = texture = 0;
        nativeBuffer = com.mojang.blaze3d.systems.RenderSystem.getDevice().createBuffer(() -> "Amnetic texel buffer",
                com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER
                        | com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_COPY_DST, sizeBytes);
        //?} else {
        /*buffer = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, sizeBytes, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
        texture = GL11.glGenTextures();
        select(0);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, buffer);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        *///?}
    }

    public void upload(FloatBuffer data) {
        if (uploaded && lastUpload.equals(data)) return;
        //? if >=26.2 {
        int length=data.remaining()*Float.BYTES;
        if(uploadBytes==null||uploadBytes.capacity()<length)uploadBytes=BufferUtils.createByteBuffer(length);
        var bytes=uploadBytes.clear().limit(length);
        bytes.asFloatBuffer().put(data.duplicate());
        var encoder = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(nativeBuffer.slice(0, bytes.remaining()), bytes);
        encoder.submit();
        //?} else {
        /*GlStateManager._glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        GL15.glBufferSubData(GL31.GL_TEXTURE_BUFFER, 0L, data);
        GlStateManager._glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
        *///?}
        lastUpload.clear();
        lastUpload.put(data.duplicate()).flip();
        uploaded = true;
    }

    public void bind(int unit) {
        //? if >=26.2 {
        throw new IllegalStateException("Bind native texel buffers by name on a RenderPass");
        //?} else {
        /*select(unit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
        select(0);
        *///?}
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
        //? if >=26.2 {
        nativeBuffer.close();
        //?} else {
        /*GL11.glDeleteTextures(texture);
        GlStateManager._glDeleteBuffers(buffer);
        *///?}
    }
}
