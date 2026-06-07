package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.instanced.InstanceBatch;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL13;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;

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
    private boolean textureRegistered;

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
        drawNow(projView, ctx.projectionMatrix(), ctx.viewMatrix(), ctx.gameTime(), instanceCount);
    }

    private void drawNow(Matrix4f projView, org.joml.Matrix4fc projection, org.joml.Matrix4fc view, float time, int instanceCount) {
        mesh.renderState().apply();

        try {
            shader.bind();
            shader.uploadProjView(projView);
            shader.uploadProjection(projection);
            shader.uploadView(view);
            shader.uploadTime(time);
            bindTextureIfNeeded();
            bindExtraSamplers();

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

    private void bindTextureIfNeeded() {
        Identifier textureId = mesh.textureId();
        if (textureId == null) return;

        var textureManager = Minecraft.getInstance().getTextureManager();
        if (!textureRegistered) {
            textureManager.registerAndLoad(textureId, new SimpleTexture(textureId));
            textureRegistered = true;
        }
        AbstractTexture texture = textureManager.getTexture(textureId);
        if (texture.getTexture() instanceof GlTexture glTexture) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(glTexture.glId());
            shader.uploadTextureSampler(0);
        }
    }

    private void bindExtraSamplers() {
        var samplers = mesh.extraSamplers();
        if (samplers.isEmpty()) return;
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (var sampler : samplers) {
            try {
                AbstractTexture texture = textureManager.getTexture(sampler.textureId());
                if (texture != null && texture.getTexture() instanceof GlTexture glTexture) {
                    GlStateManager._activeTexture(GL13.GL_TEXTURE0 + sampler.unit());
                    GlStateManager._bindTexture(glTexture.glId());
                    shader.uploadSamplerUnit(sampler.uniformName(), sampler.unit());
                }
            } catch (IllegalStateException ignored) {
            }
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
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
        GlStateManager._vertexAttribPointer(0, 3, GL11.GL_FLOAT, false, mesh.geometry().vertexStrideBytes(), 0L);
        GlStateManager._enableVertexAttribArray(0);
        if (mesh.geometry().hasTextureCoordinates()) {
            GlStateManager._vertexAttribPointer(1, 2, GL11.GL_FLOAT, false, mesh.geometry().vertexStrideBytes(), 12L);
            GlStateManager._enableVertexAttribArray(1);
        }

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
