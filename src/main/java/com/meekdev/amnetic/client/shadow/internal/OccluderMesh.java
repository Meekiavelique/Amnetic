package com.meekdev.amnetic.client.shadow.internal;

import net.minecraft.world.phys.shapes.Shapes;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.List;

public final class OccluderMesh implements AutoCloseable {

    private static final int FLOATS_PER_BOX = 36 * 3; // 12 triangles * 3 verts * 3 floats

    public final int anchorX, anchorY, anchorZ;
    private int opaqueVao, opaqueVbo, opaqueCount;
    private int cutoutVao, cutoutVbo, cutoutCount;
    private int translucentVao, translucentVbo, translucentCount;

    private OccluderMesh(int anchorX, int anchorY, int anchorZ) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
    }

    public static OccluderMesh build(List<OccluderEntry> list, int anchorX, int anchorY, int anchorZ) {
        OccluderMesh m = new OccluderMesh(anchorX, anchorY, anchorZ);
        m.buildOpaque(list);
        int[] cut = m.buildTextured(list, false);
        m.cutoutVao = cut[0]; m.cutoutVbo = cut[1]; m.cutoutCount = cut[2];
        int[] tr = m.buildTextured(list, true);
        m.translucentVao = tr[0]; m.translucentVbo = tr[1]; m.translucentCount = tr[2];
        if (m.opaqueCount == 0 && m.cutoutCount == 0 && m.translucentCount == 0) { m.close(); return null; }
        return m;
    }

    public boolean hasOpaque() { return opaqueCount > 0; }
    public boolean hasCutout() { return cutoutCount > 0; }
    public boolean hasTranslucent() { return translucentCount > 0; }

    public void drawOpaque() { drawVao(opaqueVao, opaqueCount); }
    public void drawCutout() { drawVao(cutoutVao, cutoutCount); }
    public void drawTranslucent() { drawVao(translucentVao, translucentCount); }

    private static void drawVao(int vao, int count) {
        if (count == 0) return;
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, count);
        GL30.glBindVertexArray(0);
    }

    private void buildOpaque(List<OccluderEntry> list) {
        int[] boxes = {0};
        Shapes.DoubleLineConsumer counter = (a, b, c, d, e, f) -> boxes[0]++;
        for (OccluderEntry e : list) {
            if (e.shape != null) e.shape.forAllBoxes(counter);
        }
        if (boxes[0] == 0) return;

        FloatBuffer buf = BufferUtils.createFloatBuffer(boxes[0] * FLOATS_PER_BOX);
        for (OccluderEntry e : list) {
            if (e.shape == null) continue;
            final float ox = e.pos.getX() - anchorX, oy = e.pos.getY() - anchorY, oz = e.pos.getZ() - anchorZ;
            e.shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                    emitBox(buf,
                            ox + (float) minX, oy + (float) minY, oz + (float) minZ,
                            ox + (float) maxX, oy + (float) maxY, oz + (float) maxZ));
        }
        buf.flip();

        opaqueCount = buf.limit() / 3;
        opaqueVao = GL30.glGenVertexArrays();
        opaqueVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(opaqueVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, opaqueVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private int[] buildTextured(List<OccluderEntry> list, boolean translucent) {
        int floats = 0;
        for (OccluderEntry e : list) {
            float[] v = translucent ? e.translucentVerts : e.cutoutVerts;
            if (v != null) floats += v.length;
        }
        if (floats == 0) return new int[]{0, 0, 0};

        FloatBuffer buf = BufferUtils.createFloatBuffer(floats);
        for (OccluderEntry e : list) {
            float[] v = translucent ? e.translucentVerts : e.cutoutVerts;
            if (v == null) continue;
            final float ox = e.pos.getX() - anchorX, oy = e.pos.getY() - anchorY, oz = e.pos.getZ() - anchorZ;
            for (int i = 0; i + 4 < v.length; i += 5) {
                buf.put(v[i] + ox).put(v[i + 1] + oy).put(v[i + 2] + oz).put(v[i + 3]).put(v[i + 4]);
            }
        }
        buf.flip();

        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        int stride = 5 * Float.BYTES;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 3 * Float.BYTES);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return new int[]{vao, vbo, buf.limit() / 5};
    }

    @Override
    public void close() {
        if (opaqueVbo != 0) { GL15.glDeleteBuffers(opaqueVbo); opaqueVbo = 0; }
        if (opaqueVao != 0) { GL30.glDeleteVertexArrays(opaqueVao); opaqueVao = 0; }
        if (cutoutVbo != 0) { GL15.glDeleteBuffers(cutoutVbo); cutoutVbo = 0; }
        if (cutoutVao != 0) { GL30.glDeleteVertexArrays(cutoutVao); cutoutVao = 0; }
        if (translucentVbo != 0) { GL15.glDeleteBuffers(translucentVbo); translucentVbo = 0; }
        if (translucentVao != 0) { GL30.glDeleteVertexArrays(translucentVao); translucentVao = 0; }
        opaqueCount = cutoutCount = translucentCount = 0;
    }

    private static void emitBox(FloatBuffer b,
                                float x1, float y1, float z1, float x2, float y2, float z2) {
        quad(b, x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1); // -X
        quad(b, x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2); // +X
        quad(b, x1,y1,z2, x1,y1,z1, x2,y1,z1, x2,y1,z2); // -Y
        quad(b, x1,y2,z1, x1,y2,z2, x2,y2,z2, x2,y2,z1); // +Y
        quad(b, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1); // -Z
        quad(b, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2); // +Z
    }

    private static void quad(FloatBuffer b,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        b.put(ax).put(ay).put(az); b.put(bx).put(by).put(bz); b.put(cx).put(cy).put(cz);
        b.put(ax).put(ay).put(az); b.put(cx).put(cy).put(cz); b.put(dx).put(dy).put(dz);
    }
}
