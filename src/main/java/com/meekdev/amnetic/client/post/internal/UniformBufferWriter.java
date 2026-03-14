package com.meekdev.amnetic.client.post.internal;

import com.meekdev.amnetic.mixin.accessor.GlGpuBufferAccessor;
import com.meekdev.amnetic.mixin.accessor.PostEffectPassAccessor;
import com.meekdev.amnetic.mixin.accessor.PostEffectProcessorAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.gl.GlGpuBuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.UniformValue;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class UniformBufferWriter implements AutoCloseable {

    private final Map<String, Integer> stagingBuffers = new HashMap<>();

    void update(PostEffectProcessor processor, Map<String, List<UniformValue>> uniforms) {
        if (uniforms.isEmpty()) return;

        List<PostEffectPass> passes = ((PostEffectProcessorAccessor) processor).amnetic$getPasses();
        for (PostEffectPass pass : passes) {
            Map<String, GpuBuffer> uniformBuffers = ((PostEffectPassAccessor) pass).amnetic$getUniformBuffers();
            for (Map.Entry<String, List<UniformValue>> entry : uniforms.entrySet()) {
                GpuBuffer dest = uniformBuffers.get(entry.getKey());
                if (!(dest instanceof GlGpuBuffer)) continue;

                int destId = ((GlGpuBufferAccessor) dest).amnetic$getId();
                int size = computeSize(entry.getValue());

                int stagingId = getOrCreateStaging(entry.getKey(), size);

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    ByteBuffer buf = stack.malloc(size);
                    Std140Builder builder = Std140Builder.intoBuffer(buf);
                    for (UniformValue value : entry.getValue()) {
                        value.write(builder);
                    }
                    buf.flip();
                    GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, stagingId);
                    GL15.glBufferSubData(GL31.GL_COPY_READ_BUFFER, 0, buf);
                    GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
                }

                GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, stagingId);
                GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, destId);
                GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0, 0, Math.min(size, (int) dest.size()));
                GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
                GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
            }
        }
    }

    private int getOrCreateStaging(String name, int size) {
        Integer existing = stagingBuffers.get(name);
        if (existing != null) return existing;

        int id = GL15.glGenBuffers();
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, id);
        GL15.glBufferData(GL31.GL_COPY_READ_BUFFER, size, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
        stagingBuffers.put(name, id);
        return id;
    }

    private int computeSize(List<UniformValue> values) {
        Std140SizeCalculator calc = new Std140SizeCalculator();
        for (UniformValue v : values) v.addSize(calc);
        return Math.max(calc.get(), 16);
    }

    @Override
    public void close() {
        if (!stagingBuffers.isEmpty()) {
            int[] ids = stagingBuffers.values().stream().mapToInt(Integer::intValue).toArray();
            GL15.glDeleteBuffers(ids);
            stagingBuffers.clear();
        }
    }
}
