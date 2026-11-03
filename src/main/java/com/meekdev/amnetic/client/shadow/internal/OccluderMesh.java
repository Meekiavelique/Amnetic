package com.meekdev.amnetic.client.shadow.internal;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.List;

public final class OccluderMesh implements AutoCloseable {
    private static final int POSITION_FLOATS = 3;
    private static final int TEXTURED_FLOATS = 5;
    private static final int FLOATS_PER_FACE = 6 * POSITION_FLOATS;
    private static final int NEG_X = 1;
    private static final int POS_X = 2;
    private static final int NEG_Y = 4;
    private static final int POS_Y = 8;
    private static final int NEG_Z = 16;
    private static final int POS_Z = 32;
    private static final int ALL_FACES = 0b111111;

    private record TexturedBuffer(int vao, int vbo, int count) {}

    public final int anchorX;
    public final int anchorY;
    public final int anchorZ;
    private int opaqueVao;
    private int opaqueVbo;
    private int opaqueCount;
    private int cutoutVao;
    private int cutoutVbo;
    private int cutoutCount;
    private int translucentVao;
    private int translucentVbo;
    private int translucentCount;

    private OccluderMesh(int anchorX, int anchorY, int anchorZ) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
    }

    public interface Neighbours {
        boolean full(int x, int y, int z);
    }

    public static OccluderMesh build(List<OccluderEntry> list, int anchorX, int anchorY, int anchorZ) {
        return build(list, anchorX, anchorY, anchorZ, null);
    }

    public static OccluderMesh build(List<OccluderEntry> list, int anchorX, int anchorY, int anchorZ, Neighbours outside) {
        OccluderMesh m = new OccluderMesh(anchorX, anchorY, anchorZ);
        m.buildOpaque(list, outside);
        TexturedBuffer cutout = m.buildTextured(list, false);
        m.cutoutVao = cutout.vao();
        m.cutoutVbo = cutout.vbo();
        m.cutoutCount = cutout.count();
        TexturedBuffer translucent = m.buildTextured(list, true);
        m.translucentVao = translucent.vao();
        m.translucentVbo = translucent.vbo();
        m.translucentCount = translucent.count();
        if (m.opaqueCount == 0 && m.cutoutCount == 0 && m.translucentCount == 0) {
            m.close();
            return null;
        }
        return m;
    }

    public boolean hasOpaque() {
        return opaqueCount > 0;
    }

    public boolean hasCutout() {
        return cutoutCount > 0;
    }

    public boolean hasTranslucent() {
        return translucentCount > 0;
    }

    public void drawOpaque() {
        drawVao(opaqueVao, opaqueCount);
    }

    public void drawCutout() {
        drawVao(cutoutVao, cutoutCount);
    }

    public void drawTranslucent() {
        drawVao(translucentVao, translucentCount);
    }

    private static void drawVao(int vao, int count) {
        if (count == 0) return;
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, count);
        GL30.glBindVertexArray(0);
    }

    private void buildOpaque(List<OccluderEntry> list, Neighbours outside) {
        LongOpenHashSet full = new LongOpenHashSet(list.size());
        for (OccluderEntry e : list) {
            if (e.full) full.add(e.pos.asLong());
        }
        int[] faces = {0};
        Shapes.DoubleLineConsumer counter = (a, b, c, d, e, f) -> faces[0] += 6;
        for (OccluderEntry e : list) {
            if (e.shape == null) continue;
            if (full.contains(e.pos.asLong())) {
                faces[0] += Integer.bitCount(openFaces(full, outside, e.pos));
            } else {
                e.shape.forAllBoxes(counter);
            }
        }
        if (faces[0] == 0) return;

        FloatBuffer buf = BufferUtils.createFloatBuffer(faces[0] * FLOATS_PER_FACE);
        for (OccluderEntry e : list) {
            if (e.shape == null) continue;
            final float ox = e.pos.getX() - anchorX;
            final float oy = e.pos.getY() - anchorY;
            final float oz = e.pos.getZ() - anchorZ;
            if (full.contains(e.pos.asLong())) {
                emitBox(buf, ox, oy, oz, ox + 1f, oy + 1f, oz + 1f, openFaces(full, outside, e.pos));
                continue;
            }
            e.shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                    emitBox(buf,
                            ox + (float) minX, oy + (float) minY, oz + (float) minZ,
                            ox + (float) maxX, oy + (float) maxY, oz + (float) maxZ, ALL_FACES));
        }
        if (buf.position() == 0) return;
        buf.flip();

        opaqueCount = buf.limit() / POSITION_FLOATS;
        opaqueVao = GL30.glGenVertexArrays();
        opaqueVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(opaqueVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, opaqueVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, POSITION_FLOATS, GL11.GL_FLOAT, false, POSITION_FLOATS * Float.BYTES, 0L);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private TexturedBuffer buildTextured(List<OccluderEntry> list, boolean translucent) {
        int floats = 0;
        for (OccluderEntry e : list) {
            float[] v = translucent ? e.translucentVerts : e.cutoutVerts;
            if (v != null) floats += v.length;
        }
        if (floats == 0) return new TexturedBuffer(0, 0, 0);

        FloatBuffer buf = BufferUtils.createFloatBuffer(floats);
        for (OccluderEntry e : list) {
            float[] v = translucent ? e.translucentVerts : e.cutoutVerts;
            if (v == null) continue;
            final float ox = e.pos.getX() - anchorX;
            final float oy = e.pos.getY() - anchorY;
            final float oz = e.pos.getZ() - anchorZ;
            for (int i = 0; i + 4 < v.length; i += TEXTURED_FLOATS) {
                buf.put(v[i] + ox).put(v[i + 1] + oy).put(v[i + 2] + oz).put(v[i + 3]).put(v[i + 4]);
            }
        }
        buf.flip();

        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        int stride = TEXTURED_FLOATS * Float.BYTES;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, POSITION_FLOATS, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, POSITION_FLOATS * Float.BYTES);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return new TexturedBuffer(vao, vbo, buf.limit() / TEXTURED_FLOATS);
    }

    @Override
    public void close() {
        deleteBuffer(opaqueVbo);
        deleteVertexArray(opaqueVao);
        deleteBuffer(cutoutVbo);
        deleteVertexArray(cutoutVao);
        deleteBuffer(translucentVbo);
        deleteVertexArray(translucentVao);
        opaqueVbo = 0;
        opaqueVao = 0;
        cutoutVbo = 0;
        cutoutVao = 0;
        translucentVbo = 0;
        translucentVao = 0;
        opaqueCount = 0;
        cutoutCount = 0;
        translucentCount = 0;
    }

    private static void deleteBuffer(int buffer) {
        if (buffer != 0) GL15.glDeleteBuffers(buffer);
    }

    private static void deleteVertexArray(int vao) {
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
    }

    private static int openFaces(LongOpenHashSet full, Neighbours outside, BlockPos p) {
        int x = p.getX();
        int y = p.getY();
        int z = p.getZ();
        int open = 0;
        if (!closed(full, outside, x - 1, y, z)) open |= NEG_X;
        if (!closed(full, outside, x + 1, y, z)) open |= POS_X;
        if (!closed(full, outside, x, y - 1, z)) open |= NEG_Y;
        if (!closed(full, outside, x, y + 1, z)) open |= POS_Y;
        if (!closed(full, outside, x, y, z - 1)) open |= NEG_Z;
        if (!closed(full, outside, x, y, z + 1)) open |= POS_Z;
        return open;
    }

    private static boolean closed(LongOpenHashSet full, Neighbours outside, int x, int y, int z) {
        return full.contains(BlockPos.asLong(x, y, z)) || outside != null && outside.full(x, y, z);
    }

    static void emitBox(FloatBuffer b, float x1, float y1, float z1, float x2, float y2, float z2) {
        emitBox(b, x1, y1, z1, x2, y2, z2, ALL_FACES);
    }

    private static void emitBox(FloatBuffer b,
                                float x1, float y1, float z1, float x2, float y2, float z2, int open) {
        if ((open & NEG_X) != 0) quad(b, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
        if ((open & POS_X) != 0) quad(b, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2);
        if ((open & NEG_Y) != 0) quad(b, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2);
        if ((open & POS_Y) != 0) quad(b, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        if ((open & NEG_Z) != 0) quad(b, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1);
        if ((open & POS_Z) != 0) quad(b, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
    }

    private static void quad(FloatBuffer b,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        b.put(ax).put(ay).put(az);
        b.put(bx).put(by).put(bz);
        b.put(cx).put(cy).put(cz);
        b.put(ax).put(ay).put(az);
        b.put(cx).put(cy).put(cz);
        b.put(dx).put(dy).put(dz);
    }
}
