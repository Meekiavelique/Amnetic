package com.meekdev.amnetic.client.model.internal;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.nio.ByteBuffer;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;

public final class GpuModel implements AutoCloseable {

    private static final int INSTANCE_STRIDE = 80;
    private static final int VERTEX_STRIDE_BYTES = ModelIR.VERTEX_STRIDE_FLOATS * Float.BYTES; // 32

    public record DrawInstance(Matrix4f cameraRelative, float blockLight, float skyLight) {}

    private final ModelIR ir;
    private final GpuPart[] parts;
    private final ModelTexture[] textures;          // per material index, lazily resolved
    private final Object[] resolvedKey;             // the image bytes / identifier last resolved per material
    private boolean uploaded;
    private boolean closed;

    private ByteBuffer instanceScratch = MemoryUtil.memAlloc(INSTANCE_STRIDE * 64);

    public GpuModel(ModelIR ir) {
        this.ir = ir;
        this.parts = new GpuPart[ir.parts().size()];
        this.textures = new ModelTexture[ir.materials().size()];
        this.resolvedKey = new Object[ir.materials().size()];
    }

    public ModelIR ir() { return ir; }

    private void ensureUploaded() {
        if (uploaded) return;
        for (int i = 0; i < parts.length; i++) {
            parts[i] = GpuPart.upload(ir.parts().get(i));
        }
        uploaded = true;
    }

    public void draw(ModelShader shader, List<DrawInstance> instances) {
        if (closed || instances.isEmpty()) return;
        ensureUploaded();

        int count = instances.size();
        for (GpuPart part : parts) {
            if (part == null) continue;
            packInstances(part.transform, instances, count);

            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, part.instanceVbo);
            if (count > part.instanceCapacity) {
                part.instanceCapacity = Math.max(count, part.instanceCapacity * 2);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) INSTANCE_STRIDE * part.instanceCapacity, GL15.GL_STREAM_DRAW);
            }
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, instanceScratch);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            bindMaterial(shader, part.materialIndex);

            GlStateManager._glBindVertexArray(part.vao);
            if (part.indexed) {
                GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, part.indexCount, GL11.GL_UNSIGNED_INT, 0L, count);
            } else {
                GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, part.vertexCount, count);
            }
        }
        GlStateManager._glBindVertexArray(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private void packInstances(Matrix4fc partTransform, List<DrawInstance> instances, int count) {
        int needed = count * INSTANCE_STRIDE;
        if (instanceScratch.capacity() < needed) {
            instanceScratch = MemoryUtil.memRealloc(instanceScratch, needed);
        }
        instanceScratch.clear();
        Matrix4f tmp = new Matrix4f();
        for (int i = 0; i < count; i++) {
            DrawInstance inst = instances.get(i);
            int base = i * INSTANCE_STRIDE;
            // model matrix = cameraRelativeUserTransform * bakedNodeTransform
            inst.cameraRelative().mul(partTransform, tmp);
            tmp.get(base, instanceScratch);                 // absolute write of 16 floats; position untouched
            instanceScratch.position(base + 64);
            instanceScratch.putFloat(inst.blockLight());
            instanceScratch.putFloat(inst.skyLight());
            instanceScratch.putFloat(0f);
            instanceScratch.putFloat(0f);
        }
        instanceScratch.position(0).limit(needed);
    }

    private void bindMaterial(ModelShader shader, int materialIndex) {
        ModelIR.Material mat = materialIndex >= 0 && materialIndex < ir.materials().size()
                ? ir.materials().get(materialIndex) : DEFAULT_MATERIAL;
        ModelTexture tex = resolveTexture(materialIndex, mat);
        if (tex != null) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex.id());
            shader.uploadMaterial(mat, true, 0);
        } else {
            shader.uploadMaterial(mat, false, 0);
        }
    }

    private ModelTexture resolveTexture(int materialIndex, ModelIR.Material mat) {
        if (materialIndex < 0 || materialIndex >= textures.length) return null;
        Object key = mat.baseColorImageBytes != null ? mat.baseColorImageBytes : mat.baseColorTexture;
        if (!java.util.Objects.equals(key, resolvedKey[materialIndex])) {
            resolvedKey[materialIndex] = key;
            if (textures[materialIndex] != null) { textures[materialIndex].close(); textures[materialIndex] = null; }
            if (mat.baseColorImageBytes != null) {
                textures[materialIndex] = ModelTexture.fromBytes(mat.baseColorImageBytes);
            } else if (mat.baseColorTexture != null) {
                textures[materialIndex] = ModelTexture.fromIdentifier(mat.baseColorTexture);
            }
        }
        return textures[materialIndex];
    }

    private static final ModelIR.Material DEFAULT_MATERIAL = new ModelIR.Material();

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (GpuPart p : parts) if (p != null) p.close();
        for (ModelTexture t : textures) if (t != null) t.close();
        if (instanceScratch != null) { MemoryUtil.memFree(instanceScratch); instanceScratch = null; }
    }

    private static final class GpuPart {
        int vao, vbo, ibo, instanceVbo;
        int vertexCount, indexCount, instanceCapacity = 64;
        boolean indexed;
        int materialIndex;
        final Matrix4f transform;

        private GpuPart(Matrix4f transform) { this.transform = transform; }

        static GpuPart upload(ModelIR.Part part) {
            GpuPart p = new GpuPart(part.transform);
            p.materialIndex = part.materialIndex;
            p.vertexCount = part.vertexCount();
            p.indexed = part.hasIndices();
            p.indexCount = part.hasIndices() ? part.indices.length : 0;

            p.vao = GlStateManager._glGenVertexArrays();
            GlStateManager._glBindVertexArray(p.vao);

            p.vbo = GlStateManager._glGenBuffers();
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, p.vbo);
            ByteBuffer vb = MemoryUtil.memAlloc(part.vertices.length * Float.BYTES);
            vb.asFloatBuffer().put(part.vertices);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vb, GL15.GL_STATIC_DRAW);
            MemoryUtil.memFree(vb);

            // location 0 position, 1 normal, 2 uv
            GlStateManager._vertexAttribPointer(0, 3, GL11.GL_FLOAT, false, VERTEX_STRIDE_BYTES, 0L);
            GlStateManager._enableVertexAttribArray(0);
            GlStateManager._vertexAttribPointer(1, 3, GL11.GL_FLOAT, false, VERTEX_STRIDE_BYTES, 12L);
            GlStateManager._enableVertexAttribArray(1);
            GlStateManager._vertexAttribPointer(2, 2, GL11.GL_FLOAT, false, VERTEX_STRIDE_BYTES, 24L);
            GlStateManager._enableVertexAttribArray(2);

            if (p.indexed) {
                p.ibo = GlStateManager._glGenBuffers();
                GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, p.ibo);
                ByteBuffer ib = MemoryUtil.memAlloc(part.indices.length * Integer.BYTES);
                ib.asIntBuffer().put(part.indices);
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ib, GL15.GL_STATIC_DRAW);
                MemoryUtil.memFree(ib);
            }

            // instance buffer: mat4 (loc 3-6) + vec4 light (loc 7), divisor 1
            p.instanceVbo = GlStateManager._glGenBuffers();
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, p.instanceVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) INSTANCE_STRIDE * p.instanceCapacity, GL15.GL_STREAM_DRAW);
            for (int i = 0; i < 4; i++) {
                GlStateManager._vertexAttribPointer(3 + i, 4, GL11.GL_FLOAT, false, INSTANCE_STRIDE, i * 16L);
                GlStateManager._enableVertexAttribArray(3 + i);
                GL33.glVertexAttribDivisor(3 + i, 1);
            }
            GlStateManager._vertexAttribPointer(7, 4, GL11.GL_FLOAT, false, INSTANCE_STRIDE, 64L);
            GlStateManager._enableVertexAttribArray(7);
            GL33.glVertexAttribDivisor(7, 1);

            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GlStateManager._glBindVertexArray(0);
            return p;
        }

        void close() {
            if (ibo != 0) GlStateManager._glDeleteBuffers(ibo);
            if (vbo != 0) GlStateManager._glDeleteBuffers(vbo);
            if (instanceVbo != 0) GlStateManager._glDeleteBuffers(instanceVbo);
            if (vao != 0) GL30.glDeleteVertexArrays(vao);
        }
    }
}
