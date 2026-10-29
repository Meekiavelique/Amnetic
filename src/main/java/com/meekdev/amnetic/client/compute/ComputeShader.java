package com.meekdev.amnetic.client.compute;

import com.meekdev.amnetic.client.render.ProgramLink;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryStack;

// compiled once at load(), so dev hot reload can't swap it in place; recreate the
// instance from a ShaderHotReload.onReload callback if you want live compute edits
public final class ComputeShader implements AutoCloseable {

    private final int program;

    private ComputeShader(int program) {
        this.program = program;
    }

    public static ComputeShader load(Identifier sourceId) {
        String source = readSource(sourceId);

        ProgramLink link = ProgramLink.builder("compute shader " + sourceId)
                .stage(GL43.GL_COMPUTE_SHADER, source, sourceId)
                .start();
        if (link.failed()) throw new RuntimeException("Compute shader failed (" + sourceId + "): " + link.problem());
        int program = link.program();
        return new ComputeShader(program);
    }

    public void use() {
        GL20.glUseProgram(program);
    }

    public void dispatch(int groupsX, int groupsY, int groupsZ) {
        GL20.glUseProgram(program);
        GL43.glDispatchCompute(groupsX, groupsY, groupsZ);
    }

    public static void barrier(int barriers) {
        GL42.glMemoryBarrier(barriers);
    }

    public void setInt(String name, int value) {
        GL20.glUniform1i(GL20.glGetUniformLocation(program, name), value);
    }

    public void setFloat(String name, float value) {
        GL20.glUniform1f(GL20.glGetUniformLocation(program, name), value);
    }

    public void setVec3(String name, float x, float y, float z) {
        GL20.glUniform3f(GL20.glGetUniformLocation(program, name), x, y, z);
    }

    // bind 2D texture to a texture unit and point the named sampler uniform at it
    public void setTexture(String name, int unit, int glTextureId) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTextureId);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, name), unit);
    }

    public void setMatrix4(String name, Matrix4fc matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, name), false, buffer);
        }
    }

    public int program() {
        return program;
    }

    @Override
    public void close() {
        if (program != 0) {
            GL20.glDeleteProgram(program);
        }
    }

    private static String readSource(Identifier id) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            throw new RuntimeException("Compute shader not found: " + id);
        }
        try (InputStream in = resource.get().open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read compute shader " + id, e);
        }
    }
}
