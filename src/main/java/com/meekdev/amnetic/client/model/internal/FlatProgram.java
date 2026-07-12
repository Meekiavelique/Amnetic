package com.meekdev.amnetic.client.model.internal;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

public final class FlatProgram implements AutoCloseable {

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/flat.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/flat.fsh");

    private int program;
    private int mvpLoc;
    private int colorLoc;

    public void bind() {
        ensure();
        GlStateManager._glUseProgram(program);
    }

    public void unbind() {
        GlStateManager._glUseProgram(0);
    }

    public void setColor(float r, float g, float b, float a) {
        GL20.glUniform4f(colorLoc, r, g, b, a);
    }

    public void setMvp(Matrix4fc mvp) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            mvp.get(buf);
            GL20.glUniformMatrix4fv(mvpLoc, false, buf);
        }
    }

    private void ensure() {
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
            throw new RuntimeException("Failed to link flat model shader: " + log);
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

    // dev hot reload, next ensure() recompiles from the new source
    public void invalidate() {
        close();
    }

    @Override
    public void close() {
        if (program != 0) {
            GlStateManager.glDeleteProgram(program);
            program = 0;
        }
    }
}
