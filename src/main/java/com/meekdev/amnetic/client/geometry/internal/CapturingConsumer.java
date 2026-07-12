package com.meekdev.amnetic.client.geometry.internal;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;

public final class CapturingConsumer implements VertexConsumer {

    private float[] pos = new float[3 * 256];
    private float[] uv = new float[2 * 256];
    private float[] nrm = new float[3 * 256];
    private int count;
    private int cap = 256;

    public void begin() {
        count = 0;
    }

    public int vertexCount() {
        return count;
    }

    public float[] positions() {
        return pos;
    }

    public float[] uvs() {
        return uv;
    }

    public float[] normals() {
        return nrm;
    }

    private void ensure(int verts) {
        if (verts <= cap) return;
        int n = cap;
        while (n < verts) n <<= 1;
        pos = Arrays.copyOf(pos, n * 3);
        uv = Arrays.copyOf(uv, n * 2);
        nrm = Arrays.copyOf(nrm, n * 3);
        cap = n;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        ensure(count + 1);
        int i = count * 3;
        pos[i] = x; pos[i + 1] = y; pos[i + 2] = z;
        count++;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        if (count > 0) {
            int i = (count - 1) * 2;
            uv[i] = u; uv[i + 1] = v;
        }
        return this;
    }

    @Override
    public VertexConsumer setNormal(float nx, float ny, float nz) {
        if (count > 0) {
            int i = (count - 1) * 3;
            nrm[i] = nx; nrm[i + 1] = ny; nrm[i + 2] = nz;
        }
        return this;
    }

    @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
    @Override public VertexConsumer setColor(int packed) { return this; }
    @Override public VertexConsumer setUv1(int u, int v) { return this; }
    @Override public VertexConsumer setUv2(int u, int v) { return this; }
    @Override public VertexConsumer setLineWidth(float width) { return this; }
}
