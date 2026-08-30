package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.compute.ComputeCapabilities;
import com.meekdev.amnetic.client.instanced.InstanceBatch;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.render.ImportedTextures;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

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
    private int lastInstanceCount;
    private boolean staticUploaded;
    private int uploadedGeometryVersion = -1;

    private int cullBounds;
    private int cullPayloadIn;
    private int cullIndirect;
    private boolean cullBuffersReady;
    private boolean cullStaticUploaded;
    private boolean warnedMissingBounds;

    private double lastCamX, lastCamY, lastCamZ;

    private final Set<Identifier> registeredExtras = new HashSet<>();
    private static final Vector3f SUN = new Vector3f(0f, 1f, 0f);
    private final Matrix4f projViewScratch = new Matrix4f();

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
        syncGeometry();
        if (mesh.geometry().vertexCount() == 0) return;

        projViewScratch.set(ctx.projectionMatrix()).mul(ctx.viewMatrix());

        Vec3 cam = ctx.cameraPos();
        lastCamX = cam.x;
        lastCamY = cam.y;
        lastCamZ = cam.z;

        if (gpuCullEnabled()) {
            renderGpuCulled(ctx);
        } else {
            renderCpu(ctx);
        }
    }

    private boolean gpuCullEnabled() {
        return mesh.gpuCull()
                && ComputeCapabilities.isComputeAvailable()
                && GpuCuller.INSTANCE.isAvailable();
    }

    private void renderCpu(InstanceRenderContext ctx) {
        boolean reuse = mesh.staticInstances() && staticUploaded;
        if (!reuse) {
            batch.reset();
            Vec3 cam = ctx.cameraPos();
            batch.beginFrame(projViewScratch, cam.x, cam.y, cam.z);
            mesh.onRender().accept(ctx, batch);
            int instanceCount = batch.count();
            lastInstanceCount = instanceCount;
            if (instanceCount == 0) return;
            instanceBuffer.upload(batch.flip(), instanceCount);
            staticUploaded = mesh.staticInstances();
        }
        if (lastInstanceCount == 0) return;

        drawNow(projViewScratch, ctx.projectionMatrix(), ctx.viewMatrix(), ctx.gameTime(), ctx.cameraPos(), lastInstanceCount);
    }

    private void renderGpuCulled(InstanceRenderContext ctx) {
        Vec3 cam = ctx.cameraPos();

        boolean reuse = mesh.staticInstances() && cullStaticUploaded;
        if (!reuse) {
            batch.reset();
            batch.beginFrame(projViewScratch, cam.x, cam.y, cam.z);
            mesh.onRender().accept(ctx, batch);
            lastInstanceCount = batch.count();
            if (lastInstanceCount == 0) return;

            if (!batch.hasCompleteBounds()) {
                warnMissingBoundsOnce();
                instanceBuffer.upload(batch.flip(), lastInstanceCount);
                drawNow(projViewScratch, ctx.projectionMatrix(), ctx.viewMatrix(), ctx.gameTime(), cam, lastInstanceCount);
                return;
            }

            ensureCullBuffers();
            uploadStorage(cullPayloadIn, batch.flip());
            uploadStorage(cullBounds, batch.flipBounds());
            instanceBuffer.ensureCapacity(lastInstanceCount);
            cullStaticUploaded = mesh.staticInstances();
        }
        if (lastInstanceCount == 0) return;

        CullTargets targets = new CullTargets(cullBounds, cullPayloadIn, instanceBuffer.id(), cullIndirect);
        GpuCuller.INSTANCE.cull(targets, lastInstanceCount, mesh.layout().stride(), projViewScratch, cam.x, cam.y, cam.z);
        drawIndirect(ctx);
    }

    void invalidate() {
        staticUploaded = false;
        cullStaticUploaded = false;
    }

    public boolean castsShadow() { return mesh.castsShadow(); }
    public int lastInstanceCount() { return lastInstanceCount; }

    public void renderShadow(Matrix4fc lightViewProj) {
        if (lastInstanceCount == 0 || vao == 0 || shader == null) return;
        if (mesh.geometry().vertexCount() == 0) return;

        shader.bind();
        shader.uploadProjView(lightViewProj);
        shader.uploadCameraPos(lastCamX, lastCamY, lastCamZ);
        shader.uploadWorldSpace(mesh.worldSpace());
        bindTextureIfNeeded();
        bindExtraSamplers();

        GlStateManager._glBindVertexArray(vao);

        boolean fullSet = mesh.gpuCull() && cullBuffersReady && cullPayloadIn != 0;
        if (fullSet) pointInstanceAttributes(cullPayloadIn);

        try {
            if (mesh.geometry().hasIndices()) {
                GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, mesh.geometry().indexCount(), GL11.GL_UNSIGNED_INT, 0L, lastInstanceCount);
            } else {
                GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, mesh.geometry().vertexCount(), lastInstanceCount);
            }
        } finally {
            if (fullSet) pointInstanceAttributes(instanceBuffer.id());
            GlStateManager._glBindVertexArray(0);
            GlStateManager._glUseProgram(0);
        }
    }

    private void pointInstanceAttributes(int bufferId) {
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        mesh.layout().setupVaoAttributes();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private void drawNow(Matrix4f projView, Matrix4fc projection, Matrix4fc view, float time,
                         Vec3 cameraPos, int instanceCount) {
        mesh.renderState().apply();

        try {
            shader.bind();
            shader.uploadProjView(projView);
            shader.uploadProjection(projection);
            shader.uploadView(view);
            shader.uploadTime(time);
            shader.uploadCameraPos(cameraPos.x, cameraPos.y, cameraPos.z);
            shader.uploadWorldSpace(mesh.worldSpace());
            Vector3f sun = sunDirection();
            shader.uploadSunDir(sun.x, sun.y, sun.z);
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

    private void drawIndirect(InstanceRenderContext ctx) {
        mesh.renderState().apply();

        try {
            shader.bind();
            shader.uploadProjView(projViewScratch);
            shader.uploadProjection(ctx.projectionMatrix());
            shader.uploadView(ctx.viewMatrix());
            shader.uploadTime(ctx.gameTime());
            Vec3 cam = ctx.cameraPos();
            shader.uploadCameraPos(cam.x, cam.y, cam.z);
            shader.uploadWorldSpace(mesh.worldSpace());
            Vector3f sun = sunDirection();
            shader.uploadSunDir(sun.x, sun.y, sun.z);
            bindTextureIfNeeded();
            bindExtraSamplers();

            GlStateManager._glBindVertexArray(vao);
            GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, cullIndirect);

            if (mesh.geometry().hasIndices()) {
                GL40.glDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0L);
            } else {
                GL40.glDrawArraysIndirect(GL11.GL_TRIANGLES, 0L);
            }

            GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, 0);
        } finally {
            GlStateManager._glBindVertexArray(0);
            GlStateManager._glUseProgram(0);
        }
    }

    private void ensureCullBuffers() {
        if (cullBuffersReady) return;

        cullBounds = GlStateManager._glGenBuffers();
        cullPayloadIn = GlStateManager._glGenBuffers();
        cullIndirect = GlStateManager._glGenBuffers();
        writeIndirectCommand();

        cullBuffersReady = true;
    }

    private void writeIndirectCommand() {
        boolean indexed = mesh.geometry().hasIndices();
        int elementCount = indexed ? mesh.geometry().indexCount() : mesh.geometry().vertexCount();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer command = stack.ints(elementCount, 0, 0, 0, 0);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, cullIndirect);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, command, GL15.GL_DYNAMIC_DRAW);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    private void uploadStorage(int bufferId, ByteBuffer data) {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, bufferId);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    private void warnMissingBoundsOnce() {
        if (warnedMissingBounds) return;
        warnedMissingBounds = true;
        LOGGER.warn("mesh {} requested gpuCull() but its emitter never supplied bounds; drawing unculled. "
                + "call batch.add(instance, worldX, worldY, worldZ, radius)", id);
    }

    private void bindTextureIfNeeded() {
        Identifier textureId = mesh.textureId();
        if (textureId == null) return;

        var textureManager = Minecraft.getInstance().getTextureManager();
        if (!textureRegistered) {
            if (!ImportedTextures.isImported(textureId)) {
                textureManager.registerAndLoad(textureId, new SimpleTexture(textureId));
            }
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
                if (sampler.fileBacked() && !ImportedTextures.isImported(sampler.textureId())
                        && registeredExtras.add(sampler.textureId())) {
                    textureManager.registerAndLoad(sampler.textureId(), new SimpleTexture(sampler.textureId()));
                }
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

    private static Vector3f sunDirection() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return SUN.set(0f, 1f, 0f);
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float ticks = (level.getDefaultClockTime() % 24000L) + partial;
        double phi = ((ticks - 6000.0) / 24000.0) * 2.0 * Math.PI;
        return SUN.set((float) -Math.sin(phi), (float) Math.cos(phi), 0f);
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
        if (mesh.geometry().isMutable()) {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,
                    (long) mesh.geometry().vertexCapacity() * mesh.geometry().vertexStrideBytes(),
                    GL15.GL_DYNAMIC_DRAW);
        } else {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, mesh.geometry().verticesAsBuffer(), GL15.GL_STATIC_DRAW);
        }
        if (mesh.vertexLayout() != null) {
            mesh.vertexLayout().setupVaoAttributes();
        } else {
            GlStateManager._vertexAttribPointer(0, 3, GL11.GL_FLOAT, false, mesh.geometry().vertexStrideBytes(), 0L);
            GlStateManager._enableVertexAttribArray(0);
            if (mesh.geometry().hasTextureCoordinates()) {
                GlStateManager._vertexAttribPointer(1, 2, GL11.GL_FLOAT, false, mesh.geometry().vertexStrideBytes(), 12L);
                GlStateManager._enableVertexAttribArray(1);
            }
        }

        if (mesh.geometry().hasIndices()) {
            ibo = GlStateManager._glGenBuffers();
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
            if (mesh.geometry().isMutable()) {
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER,
                        (long) mesh.geometry().indexCapacity() * Integer.BYTES, GL15.GL_DYNAMIC_DRAW);
            } else {
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, mesh.geometry().indicesAsBuffer(), GL15.GL_STATIC_DRAW);
            }
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

    private void syncGeometry() {
        var geometry = mesh.geometry();
        if (!geometry.isMutable() || geometry.version() == uploadedGeometryVersion) return;
        uploadedGeometryVersion = geometry.version();
        if (geometry.vertexCount() == 0) return;

        GlStateManager._glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, geometryVbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, geometry.verticesAsBuffer());
        if (geometry.hasIndices() && geometry.indexCount() > 0) {
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
            GL15.glBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, 0L, geometry.indicesAsBuffer());
        }
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GlStateManager._glBindVertexArray(0);
    }

    @Override
    public void close() {
        batch.free();

        if (shader != null) shader.close();
        if (instanceBuffer != null) instanceBuffer.close();

        if (cullBounds != 0) GlStateManager._glDeleteBuffers(cullBounds);
        if (cullPayloadIn != 0) GlStateManager._glDeleteBuffers(cullPayloadIn);
        if (cullIndirect != 0) GlStateManager._glDeleteBuffers(cullIndirect);

        if (ibo != 0) GlStateManager._glDeleteBuffers(ibo);
        if (geometryVbo != 0) GlStateManager._glDeleteBuffers(geometryVbo);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);

        vao = 0;
        geometryVbo = 0;
        ibo = 0;
        cullBounds = 0;
        cullPayloadIn = 0;
        cullIndirect = 0;
        cullBuffersReady = false;
    }
}
