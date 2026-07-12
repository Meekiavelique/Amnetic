package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

final class CompiledShader implements AutoCloseable {

    private final int program;
    private final int projViewMatrixLoc;
    private final int projectionMatrixLoc;
    private final int viewMatrixLoc;
    private final int textureSamplerLoc;
    private final int timeLoc;
    private final int sunDirLoc;
    private final int cameraPosLoc;

    private CompiledShader(int program) {
        this.program = program;
        this.projViewMatrixLoc = GlStateManager._glGetUniformLocation(program, "ProjViewMatrix");
        this.projectionMatrixLoc = GlStateManager._glGetUniformLocation(program, "ProjectionMatrix");
        this.viewMatrixLoc = GlStateManager._glGetUniformLocation(program, "ViewMatrix");
        this.textureSamplerLoc = GlStateManager._glGetUniformLocation(program, "TextureSampler");
        this.timeLoc = GlStateManager._glGetUniformLocation(program, "Time");
        this.sunDirLoc = GlStateManager._glGetUniformLocation(program, "SunDir");
        this.cameraPosLoc = GlStateManager._glGetUniformLocation(program, "CameraPos");
    }

    static CompiledShader load(InstancedMesh<?> mesh) {
        Identifier vshId, fshId;
        if (mesh.vertexShaderId() != null && mesh.fragmentShaderId() != null) {
            vshId = toShaderPath(mesh.vertexShaderId(), ".vsh");
            fshId = toShaderPath(mesh.fragmentShaderId(), ".fsh");
        } else if (mesh.isBuiltin()) {
            String name = mesh.builtinShader().shaderId();
            vshId = Identifier.fromNamespaceAndPath("amnetic", "shaders/instance/" + name + ".vsh");
            fshId = Identifier.fromNamespaceAndPath("amnetic", "shaders/instance/" + name + ".fsh");
        } else {
            Identifier id = mesh.customShaderId();
            vshId = toShaderPath(id, ".vsh");
            fshId = toShaderPath(id, ".fsh");
        }

        String vshSrc = loadSource(vshId);
        String fshSrc = loadSource(fshId);

        int vs = compileShader(GL20.GL_VERTEX_SHADER, vshSrc, vshId);
        int fs = compileShader(GL20.GL_FRAGMENT_SHADER, fshSrc, fshId);

        int prog = GlStateManager.glCreateProgram();
        GlStateManager.glAttachShader(prog, vs);
        GlStateManager.glAttachShader(prog, fs);
        GlStateManager.glLinkProgram(prog);
        GlStateManager.glDeleteShader(vs);
        GlStateManager.glDeleteShader(fs);

        if (GlStateManager.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GlStateManager.glGetProgramInfoLog(prog, 512);
            GlStateManager.glDeleteProgram(prog);
            throw new RuntimeException("Failed to link instance shader: " + log);
        }

        CompiledShader s = new CompiledShader(prog);
        return s;
    }

    private static Identifier toShaderPath(Identifier id, String ext) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "shaders/" + id.getPath() + ext);
    }

    private static int compileShader(int type, String src, Identifier id) {
        int shader = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(shader, src);
        GlStateManager.glCompileShader(shader);
        if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GlStateManager.glGetShaderInfoLog(shader, 512);
            GlStateManager.glDeleteShader(shader);
            throw new RuntimeException("Failed to compile shader " + id + ": " + log);
        }
        return shader;
    }

    private static String loadSource(Identifier id) {
        Optional<Resource> opt = Minecraft.getInstance().getResourceManager().getResource(id);
        if (opt.isEmpty()) {
            throw new RuntimeException("Instance shader not found: " + id);
        }
        try (InputStream is = opt.get().open()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader " + id, e);
        }
    }

    int programId() { return program; }

    void bind() { GlStateManager._glUseProgram(program); }

    void unbind() { GlStateManager._glUseProgram(0); }

    void uploadMatrix(int location, Matrix4fc m) {
        if (location == -1) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            m.get(buf);
            GL20.glUniformMatrix4fv(location, false, buf);
        }
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

    void uploadSamplerUnit(String uniformName, int unit) {
        int loc = GlStateManager._glGetUniformLocation(program, uniformName);
        if (loc != -1) GL20.glUniform1i(loc, unit);
    }

    @Override
    public void close() {
        if (program != 0) GlStateManager.glDeleteProgram(program);
    }
}
