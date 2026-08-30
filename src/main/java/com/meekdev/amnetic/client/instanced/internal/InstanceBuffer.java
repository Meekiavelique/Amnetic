package com.meekdev.amnetic.client.instanced.internal;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

final class InstanceBuffer implements AutoCloseable {

    private final int stride;
    private int id;
    private int capacityInstances;

    InstanceBuffer(int stride, int initialCapacity) {
        this.stride = stride;
        this.capacityInstances = initialCapacity;
        id = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
        GlStateManager._glBufferData(GL15.GL_ARRAY_BUFFER, (long) stride * initialCapacity, GL15.GL_STREAM_DRAW);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    int id() { return id; }

    void ensureCapacity(int instances) {
        if (instances <= capacityInstances) return;
        int newCapacity = Math.max(instances, capacityInstances * 2);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) stride * newCapacity, GL15.GL_STREAM_DRAW);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        capacityInstances = newCapacity;
    }

    void upload(ByteBuffer data, int instanceCount) {
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        if (instanceCount > capacityInstances) capacityInstances = instanceCount;
    }

    @Override
    public void close() {
        if (id != 0) {
            GlStateManager._glDeleteBuffers(id);
            id = 0;
        }
    }
}
