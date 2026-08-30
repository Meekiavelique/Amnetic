package com.meekdev.amnetic.client.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class ShaderProgram implements AutoCloseable {

    private static final Map<Identifier, Supplier<String>> VIRTUAL_SOURCES = new ConcurrentHashMap<>();

    public static void registerVirtualSource(Identifier id, Supplier<String> source) {
        VIRTUAL_SOURCES.put(id, source);
    }

    private final Identifier vshId;
    private final Identifier fshId;
    private int program;
    private int vao;

    private final Map<String, Integer> uniformLocations = new HashMap<>();
    private final float[] mat4Scratch = new float[16];
    private FloatBuffer matArrayScratch;

    private int loc(String name) {
        Integer cached = uniformLocations.get(name);
        if (cached != null) {
            return cached;
        }
        int l = GlStateManager._glGetUniformLocation(program, name);
        uniformLocations.put(name, l);
        return l;
    }

    private static final Map<ShaderProgram, Boolean> LIVE =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static void invalidateAll() {
        synchronized (LIVE) {
            for (ShaderProgram sp : LIVE.keySet()) sp.invalidate();
        }
    }

    public ShaderProgram(Identifier vshId, Identifier fshId) {
        this.vshId = vshId;
        this.fshId = fshId;
        LIVE.put(this, Boolean.TRUE);
    }

    public void invalidate() {
        if (program != 0) {
            GlStateManager.glDeleteProgram(program);
            program = 0;
        }
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
        uniformLocations.clear();
    }

    private void ensure() {
        if (program != 0) return;
        int vs = compile(GL20.GL_VERTEX_SHADER, loadWithIncludes(vshId), vshId);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, loadWithIncludes(fshId), fshId);
        int prog = GlStateManager.glCreateProgram();
        GlStateManager.glAttachShader(prog, vs);
        GlStateManager.glAttachShader(prog, fs);
        GlStateManager.glLinkProgram(prog);
        GlStateManager.glDeleteShader(vs);
        GlStateManager.glDeleteShader(fs);
        if (GlStateManager.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GlStateManager.glGetProgramInfoLog(prog, 512);
            GlStateManager.glDeleteProgram(prog);
            throw new RuntimeException("Failed to link shader (" + vshId + "/" + fshId + "): " + log);
        }
        program = prog;
        vao = GlStateManager._glGenVertexArrays();
    }

    public void begin() {
        ensure();
        GlStateManager._glUseProgram(program);
        GlStateManager._glBindVertexArray(vao);
    }

    public void draw() {
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    public void setSampler(String name, int unit) {
        int loc = loc(name);
        if (loc != -1) GL20.glUniform1i(loc, unit);
    }

    public void setFloat(String name, float value) {
        int loc = loc(name);
        if (loc != -1) GL20.glUniform1f(loc, value);
    }

    public void setVec2(String name, float x, float y) {
        int loc = loc(name);
        if (loc != -1) GL20.glUniform2f(loc, x, y);
    }

    public void setVec3(String name, float x, float y, float z) {
        int loc = loc(name);
        if (loc != -1) GL20.glUniform3f(loc, x, y, z);
    }

    public void setVec4(String name, float x, float y, float z, float w) {
        int loc = loc(name);
        if (loc != -1) GL20.glUniform4f(loc, x, y, z, w);
    }

    public void setInt(String name, int value) {
        int loc = loc(name);
        if (loc != -1) GL20.glUniform1i(loc, value);
    }

    public void setMatrix4Array(String name, float[] data, int count) {
        int loc = loc(name);
        if (loc == -1) loc = loc(name + "[0]");
        if (loc == -1 || count <= 0) return;
        int floats = Math.min(data.length, count * 16);
        if (matArrayScratch == null || matArrayScratch.capacity() < floats) {
            matArrayScratch = BufferUtils.createFloatBuffer(floats);
        }
        matArrayScratch.clear();
        matArrayScratch.put(data, 0, floats).flip();
        GL20.glUniformMatrix4fv(loc, false, matArrayScratch);
    }

    public void setMatrix4(String name, Matrix4f matrix) {
        int loc = loc(name);
        if (loc == -1) return;
        matrix.get(mat4Scratch);
        GL20.glUniformMatrix4fv(loc, false, mat4Scratch);
    }

    @Override
    public void close() {
        if (program != 0) {
            GlStateManager.glDeleteProgram(program);
            program = 0;
        }
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
        uniformLocations.clear();
    }

    private static int compile(int type, String src, Identifier id) {
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

    private static String loadWithIncludes(Identifier id) {
        StringBuilder out = new StringBuilder();
        resolve(id, out, new LinkedHashSet<>());
        return out.toString();
    }

    private static void resolve(Identifier id, StringBuilder out, Set<Identifier> seen) {
        if (!seen.add(id)) return;
        for (String line : loadRaw(id).split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include")) {
                int a = trimmed.indexOf('"');
                int b = trimmed.lastIndexOf('"');
                if (a >= 0 && b > a) {
                    resolve(Identifier.parse(trimmed.substring(a + 1, b)), out, seen);
                    continue;
                }
            }
            out.append(line).append('\n');
        }
    }

    public static String readSource(Identifier id) {
        return loadWithIncludes(id);
    }

    private static String loadRaw(Identifier id) {
        Supplier<String> virtual = VIRTUAL_SOURCES.get(id);
        if (virtual != null) return virtual.get();
        Optional<Resource> opt = Minecraft.getInstance().getResourceManager().getResource(id);
        if (opt.isEmpty()) {
            throw new RuntimeException("Shader not found: " + id);
        }
        try (InputStream is = opt.get().open()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader " + id, e);
        }
    }
}
