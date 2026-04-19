package com.meekdev.amnetic.client.instanced;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class InstanceBatch<T> {

    private final InstanceWriter<T> writer;
    private final int stride;
    private final InstancePacker packer = new InstancePacker();

    private ByteBuffer buffer;
    private int count;

    public InstanceBatch(InstanceWriter<T> writer, int stride) {
        this.writer = writer;
        this.stride = stride;
        this.buffer = MemoryUtil.memAlloc(stride * 64);
    }

    public void add(T instance) {
        if (buffer.remaining() < stride) grow();
        packer.buf = buffer;
        writer.write(instance, packer);
        count++;
    }

    private void grow() {
        int pos = buffer.position();
        buffer = MemoryUtil.memRealloc(buffer, buffer.capacity() * 2);
        buffer.position(pos);
    }

    public ByteBuffer flip() {
        buffer.flip();
        return buffer;
    }

    public int count() { return count; }

    public void reset() {
        buffer.clear();
        count = 0;
    }

    public void free() {
        MemoryUtil.memFree(buffer);
    }
}
