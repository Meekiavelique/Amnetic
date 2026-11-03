package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.render.ProgramLink;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

public final class FlatProgram implements AutoCloseable {

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/flat.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/flat.fsh");

    private int program;
    private int mvpLoc;
    private int colorLoc;
    private int skinnedLoc;
    private int jointsLoc;

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

    void setSkinned(boolean skinned) {
        if (skinnedLoc != -1) {
            GL20.glUniform1i(skinnedLoc, skinned ? 1 : 0);
        }
    }

    void uploadJointMatrices(Matrix4f[] palette, int count) {
        if (jointsLoc == -1 || count <= 0) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(count * 16);
            for (int i = 0; i < count; i++) {
                palette[i].get(i * 16, buf);
            }
            GL20.glUniformMatrix4fv(jointsLoc, false, buf);
        }
    }

    private void ensure() {
        if (program != 0) {
            return;
        }
        ProgramLink link = ProgramLink.builder("flat model shader").vertex(load(VSH), VSH).fragment(load(FSH), FSH).start();
        if (link.failed()) throw new RuntimeException("Failed to build flat model shader: " + link.problem());
        program = link.program();
        mvpLoc = GlStateManager._glGetUniformLocation(program, "Mvp");
        colorLoc = GlStateManager._glGetUniformLocation(program, "Color");
        skinnedLoc = GlStateManager._glGetUniformLocation(program, "Skinned");
        jointsLoc = GlStateManager._glGetUniformLocation(program, "JointMatrices");
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
