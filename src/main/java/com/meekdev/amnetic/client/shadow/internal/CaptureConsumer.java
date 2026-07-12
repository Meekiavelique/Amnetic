package com.meekdev.amnetic.client.shadow.internal;

import com.mojang.blaze3d.vertex.VertexConsumer;

public final class CaptureConsumer implements VertexConsumer {

    private float[] verts = new float[4096];
    private int count; // number of floats written (3 per vertex)

    public void reset() { count = 0; }

    // raw captured vertices in addVertex order, 4 consecutive = one quad
    public float[] data() { return verts; }
    public int vertexCount() { return count / 3; }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (count + 3 > verts.length) {
            float[] grown = new float[verts.length * 2];
            System.arraycopy(verts, 0, grown, 0, count);
            verts = grown;
        }
        verts[count++] = x;
        verts[count++] = y;
        verts[count++] = z;
        return this;
    }

    @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
    @Override public VertexConsumer setColor(int packed) { return this; }
    @Override public VertexConsumer setUv(float u, float v) { return this; }
    @Override public VertexConsumer setUv1(int u, int v) { return this; }
    @Override public VertexConsumer setUv2(int u, int v) { return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    @Override public VertexConsumer setLineWidth(float width) { return this; }
}
