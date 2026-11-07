package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;

import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.render.ProgramLink;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

final class CompiledShader implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Instance");

    private final Identifier vshId;
    private final Identifier fshId;
    private ProgramLink link;
    private boolean failed;
    private int program;
    private int projViewMatrixLoc;
    private int projectionMatrixLoc;
    private int viewMatrixLoc;
    private int textureSamplerLoc;
    private int timeLoc;
    private int sunDirLoc;
    private int cameraPosLoc;
    private int worldSpaceLoc;
    private int perInstanceCastLoc;
    private int depthPassLoc;
    private final Map<String, Integer> customLocations = new HashMap<>();

    private CompiledShader(Identifier vshId, Identifier fshId, ProgramLink link) {
        this.vshId = vshId;
        this.fshId = fshId;
        this.link = link;
    }

    private void linked(int program) {
        this.program = program;
        this.projViewMatrixLoc = GlStateManager._glGetUniformLocation(program, "ProjViewMatrix");
        this.projectionMatrixLoc = GlStateManager._glGetUniformLocation(program, "ProjectionMatrix");
        this.viewMatrixLoc = GlStateManager._glGetUniformLocation(program, "ViewMatrix");
        this.textureSamplerLoc = GlStateManager._glGetUniformLocation(program, "TextureSampler");
        this.timeLoc = GlStateManager._glGetUniformLocation(program, "Time");
        this.sunDirLoc = GlStateManager._glGetUniformLocation(program, "SunDir");
        this.cameraPosLoc = GlStateManager._glGetUniformLocation(program, "CameraPos");
        this.worldSpaceLoc = GlStateManager._glGetUniformLocation(program, "WorldSpace");
        this.perInstanceCastLoc = GlStateManager._glGetUniformLocation(program, "PerInstanceCast");
        this.depthPassLoc = GlStateManager._glGetUniformLocation(program, "DepthPass");
    }

    static CompiledShader load(InstancedMesh<?> mesh) {
        Identifier vshId, fshId;
        if (mesh.vertexShaderId() != null && mesh.fragmentShaderId() != null) {
            vshId = toShaderPath(mesh.vertexShaderId(), ".vsh");
            fshId = toShaderPath(mesh.fragmentShaderId(), ".fsh");
        } else if (mesh.isBuiltin()) {
            String name = mesh.builtinShader().shaderId() + (mesh.flatShaded() ? "_flat" : "");
            vshId = Identifier.fromNamespaceAndPath("amnetic", "shaders/instance/" + name + ".vsh");
            fshId = Identifier.fromNamespaceAndPath("amnetic", "shaders/instance/" + name + ".fsh");
        } else {
            Identifier id = mesh.customShaderId();
            vshId = toShaderPath(id, ".vsh");
            fshId = toShaderPath(id, ".fsh");
        }

        return load(vshId, fshId);
    }

    static CompiledShader load(Identifier vshId, Identifier fshId) {
        ProgramLink link = ProgramLink.builder("instance shader " + vshId + "/" + fshId)
                .vertex(loadSource(vshId), vshId)
                .fragment(loadSource(fshId), fshId)
                .start();
        return new CompiledShader(vshId, fshId, link);
    }

    boolean ready() {
        if (program != 0) return true;
        if (failed || link == null || !link.ready()) return false;
        ProgramLink done = link;
        link = null;
        if (done.failed()) {
            failed = true;
            LOG.error("instance shader {}/{} does not build: {}", vshId, fshId, done.problem());
            return false;
        }
        linked(done.program());
        return true;
    }

    private static Identifier toShaderPath(Identifier id, String ext) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "shaders/" + id.getPath() + ext);
    }

    private static String loadSource(Identifier id) {
        return ShaderProgram.readSource(id);
    }

    int programId() { return program; }

    void bind() {
        GlStateManager._glUseProgram(program);
        GBufferTargets.INSTANCE.program(program);
    }

    void unbind() { GlStateManager._glUseProgram(0); }

    void uploadMatrix(int location, Matrix4fc m) {
        if (location == -1) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            m.get(buf);
            GL20.glUniformMatrix4fv(location, false, buf);
        }
    }

    void uploadCustom(String name, float[] values) {
        int location = customLocations.computeIfAbsent(name, key -> GlStateManager._glGetUniformLocation(program, key));
        if (location == -1 || values == null) return;
        switch (values.length) {
            case 1 -> GL20.glUniform1f(location, values[0]);
            case 2 -> GL20.glUniform2f(location, values[0], values[1]);
            case 3 -> GL20.glUniform3f(location, values[0], values[1], values[2]);
            case 4 -> GL20.glUniform4f(location, values[0], values[1], values[2], values[3]);
            default -> { }
        }
    }

    void uploadPerInstanceCast(boolean perInstance) {
        if (perInstanceCastLoc == -1) return;
        GL20.glUniform1i(perInstanceCastLoc, perInstance ? 1 : 0);
    }

    void uploadDepthPass(boolean depthOnly) {
        if (depthPassLoc != -1) GL20.glUniform1i(depthPassLoc, depthOnly ? 1 : 0);
    }

    void uploadProjView(Matrix4fc m) { uploadMatrix(projViewMatrixLoc, m); }

    void uploadProjection(Matrix4fc m) { uploadMatrix(projectionMatrixLoc, m); }

    void uploadView(Matrix4fc m) { uploadMatrix(viewMatrixLoc, m); }

    void uploadTextureSampler(int unit) {
        if (textureSamplerLoc != -1) GL20.glUniform1i(textureSamplerLoc, unit);
    }

    void uploadTime(float seconds) {
        if (timeLoc != -1) GL20.glUniform1f(timeLoc, seconds);
    }

    void uploadSunDir(float x, float y, float z) {
        if (sunDirLoc != -1) GL20.glUniform3f(sunDirLoc, x, y, z);
    }

    void uploadCameraPos(double x, double y, double z) {
        if (cameraPosLoc != -1) GL20.glUniform3f(cameraPosLoc, (float) x, (float) y, (float) z);
    }

    void uploadWorldSpace(boolean worldSpace) {
        if (worldSpaceLoc != -1) GL20.glUniform1i(worldSpaceLoc, worldSpace ? 1 : 0);
    }

    void uploadSamplerUnit(String uniformName, int unit) {
        int loc = GlStateManager._glGetUniformLocation(program, uniformName);
        if (loc != -1) GL20.glUniform1i(loc, unit);
    }

    @Override
    public void close() {
        if (link != null) link.delete();
        link = null;
        if (program != 0) GlStateManager.glDeleteProgram(program);
        program = 0;
    }
}
