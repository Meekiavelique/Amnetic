package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

final class CompiledShader implements AutoCloseable {

    private final int program;
    private final int projViewMatrixLoc;

    private CompiledShader(int program) {
        this.program = program;
        this.projViewMatrixLoc = GlStateManager._glGetUniformLocation(program, "ProjViewMatrix");
    }

    static CompiledShader load(InstancedMesh<?> mesh) {
        Identifier vshId, fshId;
        if (mesh.isBuiltin()) {
            String name = mesh.builtinShader().shaderId();
            vshId = Identifier.of("amnetic", "shaders/instance/" + name + ".vsh");
            fshId = Identifier.of("amnetic", "shaders/instance/" + name + ".fsh");
        } else {
            Identifier id = mesh.customShaderId();
            vshId = Identifier.of(id.getNamespace(), "shaders/" + id.getPath() + ".vsh");
            fshId = Identifier.of(id.getNamespace(), "shaders/" + id.getPath() + ".fsh");
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
        Optional<Resource> opt = MinecraftClient.getInstance().getResourceManager().getResource(id);
        if (opt.isEmpty()) {
            throw new RuntimeException("Instance shader not found: " + id);
        }
        try (InputStream is = opt.get().getInputStream()) {
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

    @Override
    public void close() {
        if (program != 0) GlStateManager.glDeleteProgram(program);
    }
}
