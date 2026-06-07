package com.meekdev.amnetic.client.compute;

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

public final class ComputeShader implements AutoCloseable {

    private final int program;

    private ComputeShader(int program) {
        this.program = program;
    }

    public static ComputeShader load(Identifier sourceId) {
        String source = readSource(sourceId);

        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new RuntimeException("Compute shader compile failed (" + sourceId + "): " + log);
        }

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, shader);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(shader);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new RuntimeException("Compute program link failed (" + sourceId + "): " + log);
        }
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
