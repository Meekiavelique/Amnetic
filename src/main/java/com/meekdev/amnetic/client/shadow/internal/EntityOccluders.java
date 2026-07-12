package com.meekdev.amnetic.client.shadow.internal;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.List;

public final class EntityOccluders {

    private final CaptureConsumer capture = new CaptureConsumer();
    private float[] tri = new float[8192]; // growable triangle vertices (xyz)
    private int triFloats;
    private int vao, vbo, vertexCount, entityCount;

    public boolean build(Level level, double lx, double ly, double lz, float range,
                         int anchorX, int anchorY, int anchorZ, boolean models, float partialTick) {
        vertexCount = 0;
        entityCount = 0;
        triFloats = 0;
        if (level == null) return false;

        AABB area = new AABB(lx - range, ly - range, lz - range, lx + range, ly + range, lz + range);
        List<Entity> entities;
        try {
            entities = level.getEntities((Entity) null, area, e -> !e.isSpectator());
        } catch (Throwable t) {
            return false;
        }
        if (entities.isEmpty()) return false;

        float r2 = range * range;
        for (Entity e : entities) {
            if (!within(e, lx, ly, lz, r2)) continue;
            boolean captured = false;
            if (models) {
                capture.reset();
                if (EntityModelCapture.capture(e, partialTick, anchorX, anchorY, anchorZ, capture)) {
                    appendCapturedQuads();
                    captured = true;
                }
            }
            if (!captured) {
                AABB bb = e.getBoundingBox();
                appendBox((float) (bb.minX - anchorX), (float) (bb.minY - anchorY), (float) (bb.minZ - anchorZ),
                        (float) (bb.maxX - anchorX), (float) (bb.maxY - anchorY), (float) (bb.maxZ - anchorZ));
            }
            entityCount++;
        }
        if (triFloats == 0) return false;

        FloatBuffer buf = BufferUtils.createFloatBuffer(triFloats);
        buf.put(tri, 0, triFloats).flip();

        if (vao == 0) {
            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);
            GL30.glBindVertexArray(0);
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        vertexCount = triFloats / 3;
        return vertexCount > 0;
    }

    public boolean hasGeometry() { return vertexCount > 0; }
    public int boxCount() { return entityCount; }

    public void draw() {
        if (vertexCount == 0) return;
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        GL30.glBindVertexArray(0);
    }

    public void dispose() {
        if (vbo != 0) { GL15.glDeleteBuffers(vbo); vbo = 0; }
        if (vao != 0) { GL30.glDeleteVertexArrays(vao); vao = 0; }
        vertexCount = 0;
    }

    private void appendCapturedQuads() {
        float[] v = capture.data();
        int verts = capture.vertexCount();
        for (int q = 0; q + 3 < verts; q += 4) {
            int a = q * 3, b = (q + 1) * 3, c = (q + 2) * 3, d = (q + 3) * 3;
            push(v[a], v[a+1], v[a+2]); push(v[b], v[b+1], v[b+2]); push(v[c], v[c+1], v[c+2]);
            push(v[a], v[a+1], v[a+2]); push(v[c], v[c+1], v[c+2]); push(v[d], v[d+1], v[d+2]);
        }
    }

    private void appendBox(float x1, float y1, float z1, float x2, float y2, float z2) {
        quad(x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1);
        quad(x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2);
        quad(x1,y1,z2, x1,y1,z1, x2,y1,z1, x2,y1,z2);
        quad(x1,y2,z1, x1,y2,z2, x2,y2,z2, x2,y2,z1);
        quad(x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1);
        quad(x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2);
    }

    private void quad(float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz) {
        push(ax,ay,az); push(bx,by,bz); push(cx,cy,cz);
        push(ax,ay,az); push(cx,cy,cz); push(dx,dy,dz);
    }

    private void push(float x, float y, float z) {
        if (triFloats + 3 > tri.length) {
            float[] grown = new float[tri.length * 2];
            System.arraycopy(tri, 0, grown, 0, triFloats);
            tri = grown;
        }
        tri[triFloats++] = x;
        tri[triFloats++] = y;
        tri[triFloats++] = z;
    }

    private static boolean within(Entity e, double lx, double ly, double lz, float r2) {
        AABB bb = e.getBoundingBox();
        double cx = (bb.minX + bb.maxX) * 0.5, cy = (bb.minY + bb.maxY) * 0.5, cz = (bb.minZ + bb.maxZ) * 0.5;
        double dx = cx - lx, dy = cy - ly, dz = cz - lz;
        return dx * dx + dy * dy + dz * dz <= r2;
    }
}
