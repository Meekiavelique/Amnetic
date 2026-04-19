package com.meekdev.amnetic.client.instanced;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;

public final class MeshData {

    private final float[] vertices;
    private final int[] indices;

    private MeshData(float[] vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
    }

    public static MeshData quad() {
        float[] v = {
            -0.5f, 0f, -0.5f,
            -0.5f, 0f,  0.5f,
             0.5f, 0f, -0.5f,
             0.5f, 0f,  0.5f,
        };
        int[] i = { 0, 1, 2, 2, 1, 3 };
        return new MeshData(v, i);
    }

    public static MeshData unitCircle(int segments) {
        if (segments < 3) throw new IllegalArgumentException("segments must be >= 3");
        float[] v = new float[(segments + 1) * 3];

        v[0] = 0f; v[1] = 0f; v[2] = 0f;
        for (int s = 0; s < segments; s++) {
            double angle = 2.0 * Math.PI * s / segments;
            v[(s + 1) * 3]     = (float) Math.cos(angle);
            v[(s + 1) * 3 + 1] = 0f;
            v[(s + 1) * 3 + 2] = (float) Math.sin(angle);
        }

        int[] idx = new int[segments * 3];
        for (int s = 0; s < segments; s++) {
            idx[s * 3]     = 0;
            idx[s * 3 + 1] = (s + 1) % segments + 1;
            idx[s * 3 + 2] = s + 1;
        }
        return new MeshData(v, idx);
    }

    public static MeshData unitCube() {
        float[] v = {

            -0.5f,-0.5f,-0.5f,  0.5f,-0.5f,-0.5f,  0.5f, 0.5f,-0.5f, -0.5f, 0.5f,-0.5f,
            -0.5f,-0.5f, 0.5f,  0.5f,-0.5f, 0.5f,  0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
            -0.5f, 0.5f, 0.5f, -0.5f, 0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,-0.5f, 0.5f,
             0.5f, 0.5f, 0.5f,  0.5f, 0.5f,-0.5f,  0.5f,-0.5f,-0.5f,  0.5f,-0.5f, 0.5f,
            -0.5f,-0.5f,-0.5f,  0.5f,-0.5f,-0.5f,  0.5f,-0.5f, 0.5f, -0.5f,-0.5f, 0.5f,
            -0.5f, 0.5f,-0.5f,  0.5f, 0.5f,-0.5f,  0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
        };
        int[] idx = new int[36];
        for (int f = 0; f < 6; f++) {
            int b = f * 4;
            idx[f*6]   = b;   idx[f*6+1] = b+1; idx[f*6+2] = b+2;
            idx[f*6+3] = b+2; idx[f*6+4] = b+3; idx[f*6+5] = b;
        }
        return new MeshData(v, idx);
    }

    public static MeshData of(float[] vertices, int[] indices) {
        return new MeshData(vertices.clone(), indices.clone());
    }

    public static MeshData of(float[] vertices) {
        return new MeshData(vertices.clone(), null);
    }

    public boolean hasIndices() { return indices != null; }
    public int vertexCount() { return vertices.length / 3; }
    public int indexCount() { return indices != null ? indices.length : 0; }

    public FloatBuffer verticesAsBuffer() {
        FloatBuffer buf = BufferUtils.createFloatBuffer(vertices.length);
        buf.put(vertices).flip();
        return buf;
    }

    public IntBuffer indicesAsBuffer() {
        if (indices == null) throw new IllegalStateException("No indices");
        IntBuffer buf = BufferUtils.createIntBuffer(indices.length);
        buf.put(indices).flip();
        return buf;
    }
}
