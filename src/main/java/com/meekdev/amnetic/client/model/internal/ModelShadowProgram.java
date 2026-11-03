package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.render.ProgramLink;
import com.meekdev.amnetic.client.render.ShaderProgram;
import org.slf4j.LoggerFactory;
import java.nio.FloatBuffer;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

// depth-only counterpart to ModelShader, used while baking custom-model geometry into the shadow maps
final class ModelShadowProgram implements AutoCloseable {

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/model/shadow.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/model/shadow.fsh");

    private int program;
    private int projViewLoc;
    private int skinnedLoc;
    private int jointMatricesLoc;
    private int alphaCutoffLoc;
    private int hasAlbedoLoc;

    private ProgramLink link;
    private boolean failed;

    private ModelShadowProgram(ProgramLink link) {
        this.link = link;
    }

    private void linked(int program) {
        this.program = program;
        this.projViewLoc = uniform("ProjViewMatrix");
        this.skinnedLoc = uniform("Skinned");
        this.jointMatricesLoc = uniform("JointMatrices");
        this.alphaCutoffLoc = uniform("AlphaCutoff");
        this.hasAlbedoLoc = uniform("HasAlbedo");
        bindSamplerUnits();
    }

    // starts compiling, it bakes once ready() says so
    static ModelShadowProgram load() {
        return new ModelShadowProgram(ProgramLink.builder("model shadow shader")
                .vertex(loadSource(VSH), VSH)
                .fragment(loadSource(FSH), FSH)
                .start());
    }

    // true once it can draw, false while the driver is still on it or when it did not build
    boolean ready() {
        if (program != 0) return true;
        if (failed || link == null || !link.ready()) return false;
        ProgramLink done = link;
        link = null;
        if (done.failed()) {
            failed = true;
            LoggerFactory.getLogger("Amnetic/Model").error("model shadow shader does not build: {}", done.problem());
            return false;
        }
        linked(done.program());
        return true;
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

    private static String loadSource(Identifier id) {
        return ShaderProgram.readSource(id);
    }

    @Override
    public void close() {
        if (link != null) link.delete();
        link = null;
        if (program != 0) {
            GlStateManager.glDeleteProgram(program);
            program = 0;
        }
    }
}
