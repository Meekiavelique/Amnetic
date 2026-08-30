package com.meekdev.amnetic.client.instanced;

import org.joml.FrustumIntersection;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class InstanceBatch<T> {

    private static final int BOUNDS_STRIDE = 4 * Float.BYTES;
    private static final int INITIAL_CAPACITY = 64;

    private final InstanceWriter<T> writer;
    private final int stride;
    private final InstancePacker packer = new InstancePacker();

    private ByteBuffer buffer;
    private int count;

    private ByteBuffer boundsBuffer;
    private int boundsCount;

    private final FrustumIntersection frustum = new FrustumIntersection();
    private boolean cullReady;
    private double camX, camY, camZ;

    public InstanceBatch(InstanceWriter<T> writer, int stride) {
        this.writer = writer;
        this.stride = stride;
        this.buffer = MemoryUtil.memAlloc(stride * INITIAL_CAPACITY);
    }

    public void add(T instance) {
        if (buffer.remaining() < stride) grow();
        packer.buf = buffer;
        writer.write(instance, packer);
        count++;
    }

    public void add(T instance, double worldX, double worldY, double worldZ, float radius) {
        add(instance);
        recordBounds(worldX, worldY, worldZ, radius);
    }

    private void recordBounds(double worldX, double worldY, double worldZ, float radius) {
        if (boundsBuffer == null) {
            boundsBuffer = MemoryUtil.memAlloc(BOUNDS_STRIDE * INITIAL_CAPACITY);
        }
        if (boundsBuffer.remaining() < BOUNDS_STRIDE) {
            int position = boundsBuffer.position();
            boundsBuffer = MemoryUtil.memRealloc(boundsBuffer, boundsBuffer.capacity() * 2);
            boundsBuffer.position(position);
        }
        boundsBuffer.putFloat((float) worldX);
        boundsBuffer.putFloat((float) worldY);
        boundsBuffer.putFloat((float) worldZ);
        boundsBuffer.putFloat(radius);
        boundsCount++;
    }

    public boolean hasCompleteBounds() {
        return count > 0 && boundsCount == count;
    }

    public ByteBuffer flipBounds() {
        boundsBuffer.flip();
        return boundsBuffer;
    }

    public void beginFrame(Matrix4fc projView, double camX, double camY, double camZ) {
        frustum.set(projView, true);
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
        this.cullReady = true;
    }

    public boolean visible(double worldX, double worldY, double worldZ, float radius) {
        if (!cullReady) return true;
        return frustum.testSphere(
                (float) (worldX - camX), (float) (worldY - camY), (float) (worldZ - camZ), radius);
    }

    public void addVisible(T instance, double worldX, double worldY, double worldZ, float radius) {
        if (visible(worldX, worldY, worldZ, radius)) add(instance);
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
        if (boundsBuffer != null) {
            boundsBuffer.clear();
        }
        boundsCount = 0;
    }

    public void free() {
        MemoryUtil.memFree(buffer);
        if (boundsBuffer != null) {
            MemoryUtil.memFree(boundsBuffer);
            boundsBuffer = null;
        }
    }
}
