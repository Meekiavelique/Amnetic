package com.example.emissive;

import com.meekdev.amnetic.client.emissive.BlockEmissive;
import com.meekdev.amnetic.client.emissive.EmissiveContext;
import com.meekdev.amnetic.client.emissive.EmissiveSources;
import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.render.ShaderProgram;
import java.nio.FloatBuffer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class EmissiveDemo {

    private static final Identifier SOURCE_ID =
            Identifier.fromNamespaceAndPath("example", "orbiting_marker");
    private static final Identifier VSH =
            Identifier.fromNamespaceAndPath("example", "shaders/emissive/marker.vsh");
    private static final Identifier FSH =
            Identifier.fromNamespaceAndPath("example", "shaders/emissive/marker.fsh");

    private static ShaderProgram program;
    private static int vao;
    private static int vbo;
    private static int vertexCount;

    private EmissiveDemo() {}

    public static void init() {
        Bloom.enable();

        BlockEmissive.enable();
        BlockEmissive.chunkRadius(4);
        BlockEmissive.intensity(1.5f);

        EmissiveSources.register(SOURCE_ID, EmissiveDemo::drawMarker);
    }

    public static void stop() {
        EmissiveSources.unregister(SOURCE_ID);
        BlockEmissive.disable();
    }

    private static void drawMarker(EmissiveContext ctx) {
        ensureMesh();
        if (vertexCount == 0) return;

        float t = ctx.level().getGameTime() * 0.05f + ctx.deltaTick() * 0.05f;
        float radius = 4f;
        float ox = (float) Math.cos(t) * radius;
        float oz = (float) Math.sin(t) * radius;
        float oy = 1.5f + (float) Math.sin(t * 2.0) * 0.5f;

        Matrix4f viewProj = new Matrix4f(ctx.projection()).mul(new Matrix4f(ctx.view()));

        if (program == null) program = new ShaderProgram(VSH, FSH);
        program.begin();
        program.setMatrix4("ViewProj", viewProj);
        program.setVec3("Offset", ox, oy, oz);
        program.setFloat("Scale", 0.35f);
        program.setVec3("GlowColor",
                2.5f + (float) Math.sin(t) * 1.5f,
                0.4f,
                3.0f);

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        GL30.glBindVertexArray(0);
    }

    private static void ensureMesh() {
        if (vao != 0) return;

        float[] c = cube();
        FloatBuffer buf = BufferUtils.createFloatBuffer(c.length);
        buf.put(c).flip();
        vertexCount = c.length / 3;

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private static float[] cube() {
        float[][] faces = {
                {-1,-1,-1,  -1,-1, 1,  -1, 1, 1,  -1, 1,-1},
                { 1,-1, 1,   1,-1,-1,   1, 1,-1,   1, 1, 1},
                {-1,-1, 1,  -1,-1,-1,   1,-1,-1,   1,-1, 1},
                {-1, 1,-1,  -1, 1, 1,   1, 1, 1,   1, 1,-1},
                { 1,-1,-1,  -1,-1,-1,  -1, 1,-1,   1, 1,-1},
                {-1,-1, 1,   1,-1, 1,   1, 1, 1,  -1, 1, 1},
        };
        float[] out = new float[faces.length * 6 * 3];
        int w = 0;
        for (float[] f : faces) {
            int[] order = {0, 1, 2, 0, 2, 3};
            for (int idx : order) {
                out[w++] = f[idx * 3];
                out[w++] = f[idx * 3 + 1];
                out[w++] = f[idx * 3 + 2];
            }
        }
        return out;
    }
}
