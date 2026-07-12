package com.meekdev.amnetic.client.render;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import com.meekdev.amnetic.client.dev.ShaderHotReload;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

public final class Geometry {

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/flat.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/flat.fsh");

    private static final Map<MeshData, Uploaded> CACHE = new IdentityHashMap<>();
    private static int program;
    private static int mvpLoc;
    private static int colorLoc;

    static {
        // dev hot reload, drop the program so the next fill() recompiles from the new source
        ShaderHotReload.onReload(Geometry::invalidateProgram);
    }

    private Geometry() {
    }

    private static void invalidateProgram() {
        if (program != 0) {
            GlStateManager.glDeleteProgram(program);
            program = 0;
        }
    }

    public static void fill(MeshData mesh, Matrix4fc modelViewProj, float r, float g, float b, float a) {
        ensureProgram();
        Uploaded gpu = CACHE.computeIfAbsent(mesh, Geometry::upload);

        GlStateManager._glUseProgram(program);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            modelViewProj.get(buf);
            GL20.glUniformMatrix4fv(mvpLoc, false, buf);
        }
        GL20.glUniform4f(colorLoc, r, g, b, a);

        GlStateManager._glBindVertexArray(gpu.vao);
        if (gpu.indexed) {
            GL11.glDrawElements(GL11.GL_TRIANGLES, gpu.count, GL11.GL_UNSIGNED_INT, 0L);
        } else {
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, gpu.count);
        }
        GlStateManager._glBindVertexArray(0);
        GlStateManager._glUseProgram(0);
    }

    public static void dispose() {
        for (Uploaded gpu : CACHE.values()) {
            gpu.delete();
        }
        CACHE.clear();
        if (program != 0) {
            GlStateManager.glDeleteProgram(program);
            program = 0;
        }
    }

    private static Uploaded upload(MeshData mesh) {
        Uploaded gpu = new Uploaded();
        gpu.indexed = mesh.hasIndices();
        gpu.count = gpu.indexed ? mesh.indexCount() : mesh.vertexCount();

        gpu.vao = GlStateManager._glGenVertexArrays();
        GlStateManager._glBindVertexArray(gpu.vao);

        gpu.vbo = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, gpu.vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, mesh.verticesAsBuffer(), GL15.GL_STATIC_DRAW);
        GlStateManager._vertexAttribPointer(0, 3, GL11.GL_FLOAT, false, mesh.vertexStrideBytes(), 0L);
        GlStateManager._enableVertexAttribArray(0);

        if (gpu.indexed) {
            gpu.ibo = GlStateManager._glGenBuffers();
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, gpu.ibo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, mesh.indicesAsBuffer(), GL15.GL_STATIC_DRAW);
        }

        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GlStateManager._glBindVertexArray(0);
        return gpu;
    }

    private static void ensureProgram() {
        if (program != 0) {
            return;
        }
        int vs = compile(GL20.GL_VERTEX_SHADER, load(VSH), VSH);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, load(FSH), FSH);
        program = GlStateManager.glCreateProgram();
        GlStateManager.glAttachShader(program, vs);
        GlStateManager.glAttachShader(program, fs);
        GlStateManager.glLinkProgram(program);
        GlStateManager.glDeleteShader(vs);
        GlStateManager.glDeleteShader(fs);
        if (GlStateManager.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GlStateManager.glGetProgramInfoLog(program, 1024);
            GlStateManager.glDeleteProgram(program);
            program = 0;
            throw new RuntimeException("Failed to link flat geometry shader: " + log);
        }
        mvpLoc = GlStateManager._glGetUniformLocation(program, "Mvp");
        colorLoc = GlStateManager._glGetUniformLocation(program, "Color");
    }

    private static int compile(int type, String src, Identifier id) {
        int shader = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(shader, src);
        GlStateManager.glCompileShader(shader);
        if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GlStateManager.glGetShaderInfoLog(shader, 1024);
            GlStateManager.glDeleteShader(shader);
            throw new RuntimeException("Failed to compile " + id + ": " + log);
        }
        return shader;
    }

    private static String load(Identifier id) {
        Optional<Resource> opt = Minecraft.getInstance().getResourceManager().getResource(id);
        if (opt.isEmpty()) {
            throw new RuntimeException("Shader not found: " + id);
        }
        try (InputStream is = opt.get().open()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read " + id, e);
        }
    }

    private static final class Uploaded {
        int vao;
        int vbo;
        int ibo;
        int count;
        boolean indexed;

        void delete() {
            if (ibo != 0) {
                GlStateManager._glDeleteBuffers(ibo);
            }
            if (vbo != 0) {
                GlStateManager._glDeleteBuffers(vbo);
            }
            if (vao != 0) {
                GL30.glDeleteVertexArrays(vao);
            }
        }
    }
}
