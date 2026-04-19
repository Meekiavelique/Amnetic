package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.instanced.InstanceBatch;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public final class InstanceMeshEntry<T> implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/Instance");

    private final Identifier id;
    private final InstancedMesh<T> mesh;
    private final InstanceBatch<T> batch;

    private int vao;
    private int geometryVbo;
    private int ibo;
    private InstanceBuffer instanceBuffer;
    private CompiledShader shader;

    InstanceMeshEntry(Identifier id, InstancedMesh<T> mesh) {
        this.id = id;
        this.mesh = mesh;
        this.batch = new InstanceBatch<>(mesh.writer(), mesh.layout().stride());
    }

    public Identifier id() { return id; }
    public InstancedMesh<T> mesh() { return mesh; }

    public void render(InstanceRenderContext ctx) {
        ensureVao();
        ensureShader();

        batch.reset();
        mesh.onRender().accept(ctx, batch);

        int instanceCount = batch.count();
        if (instanceCount == 0) return;

        ByteBuffer instanceData = batch.flip();
        instanceBuffer.upload(instanceData, instanceCount);

        Matrix4f projView = new Matrix4f(ctx.projectionMatrix()).mul(ctx.viewMatrix());
        drawNow(projView, instanceCount);
    }

    private void drawNow(Matrix4f projView, int instanceCount) {
        mesh.renderState().apply();

        try {
            shader.bind();
            shader.uploadProjView(projView);

            GlStateManager._glBindVertexArray(vao);

            if (mesh.geometry().hasIndices()) {
                GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, mesh.geometry().indexCount(), GL11.GL_UNSIGNED_INT, 0L, instanceCount);
            } else {
                GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, mesh.geometry().vertexCount(), instanceCount);
            }
        } finally {
            GlStateManager._glBindVertexArray(0);
            GlStateManager._glUseProgram(0);
        }
    }

    public void invalidateShader() {
        if (shader != null) {
            shader.close();
            shader = null;
        }
    }

    private void ensureVao() {
        if (vao != 0) return;

        vao = GlStateManager._glGenVertexArrays();
        GlStateManager._glBindVertexArray(vao);

        geometryVbo = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, geometryVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, mesh.geometry().verticesAsBuffer(), GL15.GL_STATIC_DRAW);
        GlStateManager._vertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);
        GlStateManager._enableVertexAttribArray(0);

        if (mesh.geometry().hasIndices()) {
            ibo = GlStateManager._glGenBuffers();
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, mesh.geometry().indicesAsBuffer(), GL15.GL_STATIC_DRAW);
        }

        instanceBuffer = new InstanceBuffer(mesh.layout().stride(), 64);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer.id());
        mesh.layout().setupVaoAttributes();

        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GlStateManager._glBindVertexArray(0);
    }

    private void ensureShader() {
        if (shader == null) {
            shader = CompiledShader.load(mesh);
        }
    }

    @Override
    public void close() {
        batch.free();

        if (shader != null) shader.close();
        if (instanceBuffer != null) instanceBuffer.close();

        if (ibo != 0) GlStateManager._glDeleteBuffers(ibo);
        if (geometryVbo != 0) GlStateManager._glDeleteBuffers(geometryVbo);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);

        vao = 0;
        geometryVbo = 0;
        ibo = 0;
    }
}