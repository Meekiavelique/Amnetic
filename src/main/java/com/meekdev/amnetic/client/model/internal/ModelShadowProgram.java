package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.render.ShaderProgram;
import java.nio.FloatBuffer;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

// depth-only counterpart to ModelShader, used while baking custom-model geometry into the shadow maps
final class ModelShadowProgram implements AutoCloseable {

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/model/shadow.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/model/shadow.fsh");

    private final int program;
    private final int projViewLoc;
    private final int skinnedLoc;
    private final int jointMatricesLoc;
    private final int alphaCutoffLoc;
    private final int hasAlbedoLoc;

    private ModelShadowProgram(int program) {
        this.program = program;
        this.projViewLoc = uniform("ProjViewMatrix");
        this.skinnedLoc = uniform("Skinned");
        this.jointMatricesLoc = uniform("JointMatrices");
        this.alphaCutoffLoc = uniform("AlphaCutoff");
        this.hasAlbedoLoc = uniform("HasAlbedo");
        bindSamplerUnits();
    }

    static ModelShadowProgram load() {
        int vs = compile(GL20.GL_VERTEX_SHADER, loadSource(VSH), VSH);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, loadSource(FSH), FSH);
        int prog = GlStateManager.glCreateProgram();
        GlStateManager.glAttachShader(prog, vs);
        GlStateManager.glAttachShader(prog, fs);
        GlStateManager.glLinkProgram(prog);
        GlStateManager.glDeleteShader(vs);
        GlStateManager.glDeleteShader(fs);
        if (GlStateManager.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            int length = GlStateManager.glGetProgrami(prog, GL20.GL_INFO_LOG_LENGTH);
            String log = GlStateManager.glGetProgramInfoLog(prog, Math.max(length, 512));
            GlStateManager.glDeleteProgram(prog);
            throw new RuntimeException("Failed to link model shadow shader: " + log);
        }
        return new ModelShadowProgram(prog);
    }

    void bind() {
        GlStateManager._glUseProgram(program);
    }

    void uploadProjView(Matrix4fc m) {
        if (projViewLoc == -1) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            m.get(buf);
            GL20.glUniformMatrix4fv(projViewLoc, false, buf);
        }
    }

    void setSkinned(boolean skinned) {
        if (skinnedLoc != -1) {
            GL20.glUniform1i(skinnedLoc, skinned ? 1 : 0);
        }
    }

    void uploadJointMatrices(Matrix4f[] palette, int count) {
        if (jointMatricesLoc == -1 || count <= 0) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(count * 16);
            for (int i = 0; i < count; i++) {
                palette[i].get(i * 16, buf);
            }
            buf.position(0).limit(count * 16);
            GL20.glUniformMatrix4fv(jointMatricesLoc, false, buf);
        }
    }

    void setMaterial(float alphaCutoff, boolean hasAlbedo) {
        if (alphaCutoffLoc != -1) {
            GL20.glUniform1f(alphaCutoffLoc, alphaCutoff);
        }
        if (hasAlbedoLoc != -1) {
            GL20.glUniform1i(hasAlbedoLoc, hasAlbedo ? 1 : 0);
        }
    }

    private void bindSamplerUnits() {
        GlStateManager._glUseProgram(program);
        int loc = uniform("AlbedoSampler");
        if (loc != -1) {
            GL20.glUniform1i(loc, 0);
        }
        GlStateManager._glUseProgram(0);
    }

    private int uniform(String name) {
        return GlStateManager._glGetUniformLocation(program, name);
    }

    private static int compile(int type, String src, Identifier id) {
        int shader = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(shader, src);
        GlStateManager.glCompileShader(shader);
        if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            int length = GlStateManager.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH);
            String log = GlStateManager.glGetShaderInfoLog(shader, Math.max(length, 512));
            GlStateManager.glDeleteShader(shader);
            throw new RuntimeException("Failed to compile model shadow shader " + id + ": " + log);
        }
        return shader;
    }

    private static String loadSource(Identifier id) {
        return ShaderProgram.readSource(id);
    }

    @Override
    public void close() {
        if (program != 0) {
            GlStateManager.glDeleteProgram(program);
        }
    }
}
