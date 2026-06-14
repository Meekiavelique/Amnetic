package com.meekdev.amnetic.client.model.internal;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

final class ModelShader implements AutoCloseable {

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/model/model.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/model/model.fsh");

    private final int program;
    private final int projViewLoc;
    private final int albedoLoc;
    private final int hasAlbedoLoc;
    private final int baseColorLoc;
    private final int emissiveLoc;
    private final int metallicLoc;
    private final int roughnessLoc;

    private ModelShader(int program) {
        this.program = program;
        this.projViewLoc = GlStateManager._glGetUniformLocation(program, "ProjViewMatrix");
        this.albedoLoc = GlStateManager._glGetUniformLocation(program, "AlbedoSampler");
        this.hasAlbedoLoc = GlStateManager._glGetUniformLocation(program, "HasAlbedo");
        this.baseColorLoc = GlStateManager._glGetUniformLocation(program, "BaseColor");
        this.emissiveLoc = GlStateManager._glGetUniformLocation(program, "Emissive");
        this.metallicLoc = GlStateManager._glGetUniformLocation(program, "Metallic");
        this.roughnessLoc = GlStateManager._glGetUniformLocation(program, "Roughness");
    }

    static ModelShader load() {
        int vs = compile(GL20.GL_VERTEX_SHADER, loadSource(VSH), VSH);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, loadSource(FSH), FSH);
        int prog = GlStateManager.glCreateProgram();
        GlStateManager.glAttachShader(prog, vs);
        GlStateManager.glAttachShader(prog, fs);
        GlStateManager.glLinkProgram(prog);
        GlStateManager.glDeleteShader(vs);
        GlStateManager.glDeleteShader(fs);
        if (GlStateManager.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GlStateManager.glGetProgramInfoLog(prog, 512);
            GlStateManager.glDeleteProgram(prog);
            throw new RuntimeException("Failed to link model shader: " + log);
        }
        return new ModelShader(prog);
    }

    void bind() { GlStateManager._glUseProgram(program); }

    void uploadProjView(Matrix4fc m) {
        if (projViewLoc == -1) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            m.get(buf);
            GL20.glUniformMatrix4fv(projViewLoc, false, buf);
        }
    }

    void uploadMaterial(ModelIR.Material mat, boolean hasAlbedo, int albedoUnit) {
        if (albedoLoc != -1) GL20.glUniform1i(albedoLoc, albedoUnit);
        if (hasAlbedoLoc != -1) GL20.glUniform1i(hasAlbedoLoc, hasAlbedo ? 1 : 0);
        if (baseColorLoc != -1) GL20.glUniform4f(baseColorLoc, mat.baseR, mat.baseG, mat.baseB, mat.baseA);
        if (emissiveLoc != -1) GL20.glUniform3f(emissiveLoc, mat.emR, mat.emG, mat.emB);
        if (metallicLoc != -1) GL20.glUniform1f(metallicLoc, mat.metallic);
        if (roughnessLoc != -1) GL20.glUniform1f(roughnessLoc, mat.roughness);
    }

    private static int compile(int type, String src, Identifier id) {
        int shader = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(shader, src);
        GlStateManager.glCompileShader(shader);
        if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GlStateManager.glGetShaderInfoLog(shader, 512);
            GlStateManager.glDeleteShader(shader);
            throw new RuntimeException("Failed to compile model shader " + id + ": " + log);
        }
        return shader;
    }

    private static String loadSource(Identifier id) {
        Optional<Resource> opt = Minecraft.getInstance().getResourceManager().getResource(id);
        if (opt.isEmpty()) throw new RuntimeException("Model shader not found: " + id);
        try (InputStream is = opt.get().open()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read model shader " + id, e);
        }
    }

    @Override
    public void close() {
        if (program != 0) GlStateManager.glDeleteProgram(program);
    }
}
