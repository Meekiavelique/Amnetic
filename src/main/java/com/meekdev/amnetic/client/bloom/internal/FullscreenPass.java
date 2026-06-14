package com.meekdev.amnetic.client.bloom.internal;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class FullscreenPass implements AutoCloseable {

    private final Identifier vshId;
    private final Identifier fshId;
    private int program;
    private int vao;

    public FullscreenPass(Identifier vshId, Identifier fshId) {
        this.vshId = vshId;
        this.fshId = fshId;
    }

    private void ensure() {
        if (program != 0) return;
        int vs = compile(GL20.GL_VERTEX_SHADER, load(vshId), vshId);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, load(fshId), fshId);
        int prog = GlStateManager.glCreateProgram();
        GlStateManager.glAttachShader(prog, vs);
        GlStateManager.glAttachShader(prog, fs);
        GlStateManager.glLinkProgram(prog);
        GlStateManager.glDeleteShader(vs);
        GlStateManager.glDeleteShader(fs);
        if (GlStateManager.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GlStateManager.glGetProgramInfoLog(prog, 512);
            GlStateManager.glDeleteProgram(prog);
            throw new RuntimeException("Failed to link bloom shader (" + vshId + "/" + fshId + "): " + log);
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
        int loc = GlStateManager._glGetUniformLocation(program, name);
        if (loc != -1) GL20.glUniform1i(loc, unit);
    }

    public void setFloat(String name, float value) {
        int loc = GlStateManager._glGetUniformLocation(program, name);
        if (loc != -1) GL20.glUniform1f(loc, value);
    }

    public void setVec2(String name, float x, float y) {
        int loc = GlStateManager._glGetUniformLocation(program, name);
        if (loc != -1) GL20.glUniform2f(loc, x, y);
    }

    public void setVec3(String name, float x, float y, float z) {
        int loc = GlStateManager._glGetUniformLocation(program, name);
        if (loc != -1) GL20.glUniform3f(loc, x, y, z);
    }

    public void setInt(String name, int value) {
        int loc = GlStateManager._glGetUniformLocation(program, name);
        if (loc != -1) GL20.glUniform1i(loc, value);
    }

    public void setMatrix4(String name, org.joml.Matrix4f matrix) {
        int loc = GlStateManager._glGetUniformLocation(program, name);
        if (loc == -1) return;
        float[] buf = new float[16];
        matrix.get(buf);
        GL20.glUniformMatrix4fv(loc, false, buf);
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
    }

    private static int compile(int type, String src, Identifier id) {
        int shader = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(shader, src);
        GlStateManager.glCompileShader(shader);
        if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GlStateManager.glGetShaderInfoLog(shader, 512);
            GlStateManager.glDeleteShader(shader);
            throw new RuntimeException("Failed to compile bloom shader " + id + ": " + log);
        }
        return shader;
    }

    private static String load(Identifier id) {
        Optional<Resource> opt = Minecraft.getInstance().getResourceManager().getResource(id);
        if (opt.isEmpty()) {
            throw new RuntimeException("Bloom shader not found: " + id);
        }
        try (InputStream is = opt.get().open()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read bloom shader " + id, e);
        }
    }
}
