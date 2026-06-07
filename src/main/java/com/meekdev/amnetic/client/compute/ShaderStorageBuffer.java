package com.meekdev.amnetic.client.compute;

import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

public final class ShaderStorageBuffer implements AutoCloseable {

    private final int id;
    private final long sizeBytes;

    public ShaderStorageBuffer(long sizeBytes) {
        this.sizeBytes = sizeBytes;
        this.id = GL15.glGenBuffers();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, sizeBytes, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void bind(int binding) {
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, id);
    }

    public void readInto(FloatBuffer dest) {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, dest);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void upload(FloatBuffer data) {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, data);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public int id() {
        return id;
    }

    @Override
    public void close() {
        GL15.glDeleteBuffers(id);
    }
}
