package com.meekdev.amnetic.client.shadow.internal;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class ShadowDepthProgram implements AutoCloseable {

    private final Identifier vsh, fsh;
    private int program;
    private int viewProjLoc = -1;
    private final float[] mat = new float[16];

    public ShadowDepthProgram(Identifier vsh, Identifier fsh) {
        this.vsh = vsh;
        this.fsh = fsh;
    }

    private void ensure() {
        if (program != 0) return;
        int vs = compile(GL20.GL_VERTEX_SHADER, load(vsh), vsh);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, load(fsh), fsh);
        int prog = GlStateManager.glCreateProgram();
        GlStateManager.glAttachShader(prog, vs);
        GlStateManager.glAttachShader(prog, fs);
        GL20.glBindAttribLocation(prog, 0, "Position");
        GL20.glBindAttribLocation(prog, 1, "UV");
        GlStateManager.glLinkProgram(prog);
        GlStateManager.glDeleteShader(vs);
        GlStateManager.glDeleteShader(fs);
        if (GlStateManager.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GlStateManager.glGetProgramInfoLog(prog, 512);
            GlStateManager.glDeleteProgram(prog);
            throw new RuntimeException("Failed to link shadow program " + vsh + "/" + fsh + ": " + log);
        }
        program = prog;
        viewProjLoc = GlStateManager._glGetUniformLocation(program, "uViewProj");
    }

    public void begin() {
        ensure();
        GlStateManager._glUseProgram(program);
    }

    public void setViewProj(Matrix4f m) {
        m.get(mat);
        if (viewProjLoc != -1) GL20.glUniformMatrix4fv(viewProjLoc, false, mat);
    }

    public void setSampler(String name, int unit) {
        int loc = GlStateManager._glGetUniformLocation(program, name);
        if (loc != -1) GL20.glUniform1i(loc, unit);
    }

    @Override
    public void close() {
        if (program != 0) { GlStateManager.glDeleteProgram(program); program = 0; }
    }

    private static int compile(int type, String src, Identifier id) {
        int shader = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(shader, src);
        GlStateManager.glCompileShader(shader);
        if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GlStateManager.glGetShaderInfoLog(shader, 512);
            GlStateManager.glDeleteShader(shader);
            throw new RuntimeException("Failed to compile shadow shader " + id + ": " + log);
        }
        return shader;
    }

    private static String load(Identifier id) {
        Optional<Resource> opt = Minecraft.getInstance().getResourceManager().getResource(id);
        if (opt.isEmpty()) throw new RuntimeException("Shadow shader not found: " + id);
        try (InputStream is = opt.get().open()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read shadow shader " + id, e);
        }
    }
}
