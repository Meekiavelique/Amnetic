package com.meekdev.amnetic.client.post.internal;

import com.meekdev.amnetic.mixin.accessor.GlGpuBufferAccessor;
import com.meekdev.amnetic.mixin.accessor.PostEffectPassAccessor;
import com.meekdev.amnetic.mixin.accessor.PostEffectProcessorAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import net.minecraft.client.gl.GlGpuBuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.UniformValue;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

final class UniformBufferWriter {

    private UniformBufferWriter() {}

    static void update(PostEffectProcessor processor, Map<String, List<UniformValue>> uniforms) {
        if (uniforms.isEmpty()) return;

        List<PostEffectPass> passes = ((PostEffectProcessorAccessor) processor).amnetic$getPasses();
        for (PostEffectPass pass : passes) {
            Map<String, GpuBuffer> buffers = ((PostEffectPassAccessor) pass).amnetic$getUniformBuffers();
            for (Map.Entry<String, List<UniformValue>> entry : uniforms.entrySet()) {
                GpuBuffer gpuBuffer = buffers.get(entry.getKey());
                if (!(gpuBuffer instanceof GlGpuBuffer)) continue;

                int glId = ((GlGpuBufferAccessor) gpuBuffer).amnetic$getId();
                int size = (int) gpuBuffer.size();

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    ByteBuffer buf = stack.malloc(size);
                    Std140Builder builder = Std140Builder.intoBuffer(buf);
                    for (UniformValue value : entry.getValue()) {
                        value.write(builder);
                    }
                    buf.flip();
                    GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, glId);
                    GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0, buf);
                    GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
                }
            }
        }
    }
}
