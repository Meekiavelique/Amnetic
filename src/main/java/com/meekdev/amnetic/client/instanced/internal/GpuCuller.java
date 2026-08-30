package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.compute.ComputeShader;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class GpuCuller {

    public static final GpuCuller INSTANCE = new GpuCuller();

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/GpuCuller");
    private static final Identifier SHADER_ID =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/instanced/cull.comp");

    private static final int GROUP_SIZE = 64;
    private static final int PLANE_COUNT = 6;

    private static final int BINDING_BOUNDS = 0;
    private static final int BINDING_PAYLOAD_IN = 1;
    private static final int BINDING_PAYLOAD_OUT = 2;
    private static final int BINDING_COMMAND = 3;

    private static final long INSTANCE_COUNT_OFFSET = Integer.BYTES;

    private ComputeShader shader;
    private boolean shaderFailed;
    private int planesLocation = -1;

    private final float[] planes = new float[PLANE_COUNT * 4];
    private final Vector4f planeScratch = new Vector4f();

    private GpuCuller() {}

    public boolean isAvailable() {
        ensureShader();
        return shader != null;
    }

    public void cull(CullTargets targets, int instanceCount, int strideBytes,
                     Matrix4fc cameraRelativeProjView, double camX, double camY, double camZ) {
        ensureShader();
        if (shader == null) {
            return;
        }

        resetInstanceCount(targets.command());
        extractPlanes(cameraRelativeProjView);

        shader.use();
        shader.setInt("Count", instanceCount);
        shader.setInt("StrideUints", strideBytes / Integer.BYTES);
        shader.setVec3("CamPos", (float) camX, (float) camY, (float) camZ);
        uploadPlanes();

        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING_BOUNDS, targets.bounds());
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING_PAYLOAD_IN, targets.payloadIn());
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING_PAYLOAD_OUT, targets.payloadOut());
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING_COMMAND, targets.command());

        shader.dispatch(groupCount(instanceCount), 1, 1);
        ComputeShader.barrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT
                | GL42.GL_COMMAND_BARRIER_BIT
                | GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
    }

    private void ensureShader() {
        if (shader != null || shaderFailed) {
            return;
        }
        try {
            shader = ComputeShader.load(SHADER_ID);
            planesLocation = GL20.glGetUniformLocation(shader.program(), "Planes");
        } catch (RuntimeException e) {
            shaderFailed = true;
            LOGGER.warn("GPU instance culling unavailable, falling back to CPU: {}", e.toString());
        }
    }

    private void resetInstanceCount(int commandBuffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer zero = stack.ints(0);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, commandBuffer);
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, INSTANCE_COUNT_OFFSET, zero);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        }
    }

    private void extractPlanes(Matrix4fc projView) {
        for (int plane = 0; plane < PLANE_COUNT; plane++) {
            projView.frustumPlane(plane, planeScratch);

            float length = (float) Math.sqrt(
                    planeScratch.x * planeScratch.x
                            + planeScratch.y * planeScratch.y
                            + planeScratch.z * planeScratch.z);
            float inverse = length > 0f ? 1f / length : 0f;

            int base = plane * 4;
            planes[base] = planeScratch.x * inverse;
            planes[base + 1] = planeScratch.y * inverse;
            planes[base + 2] = planeScratch.z * inverse;
            planes[base + 3] = planeScratch.w * inverse;
        }
    }

    private void uploadPlanes() {
        if (planesLocation < 0) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(planes.length);
            buffer.put(planes).flip();
            GL20.glUniform4fv(planesLocation, buffer);
        }
    }

    private static int groupCount(int instanceCount) {
        return (instanceCount + GROUP_SIZE - 1) / GROUP_SIZE;
    }
}
