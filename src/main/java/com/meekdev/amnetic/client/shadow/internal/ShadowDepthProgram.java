package com.meekdev.amnetic.client.shadow.internal;

import com.meekdev.amnetic.client.render.ProgramLink;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
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
        ProgramLink link = ProgramLink.builder("shadow program " + vsh + "/" + fsh)
                .vertex(load(vsh), vsh)
                .fragment(load(fsh), fsh)
                .attribute(0, "Position")
                .attribute(1, "UV")
                .start();
        if (link.failed()) throw new RuntimeException("Failed to build shadow program " + vsh + "/" + fsh + ": " + link.problem());
        program = link.program();
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
