package com.meekdev.amnetic.client.shadow.internal;

import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.shadow.ShadowSettings;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
//? if >=26.2 {
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
//?}
//? if >=26.2 {
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
//? } else {
//?}

public final class EntityOccluders {
    private static final long FNV_OFFSET = 1469598103934665603L;
    private static final long FNV_PRIME = 1099511628211L;
    private static final double POSITION_STEPS = 64.0;
    private static final int FLOATS_PER_VERTEX = 3;
    private final CaptureConsumer capture = new CaptureConsumer();
    private float[] tri = new float[8192];
    private int triFloats;
    private int vao;
    private int vbo;
    private int vertexCount;
    private int entityCount;
    private long builtSig;
    private final List<Entity> found = new ArrayList<>();
    private FloatBuffer upload;
    //? if >=26.2 {
    private GpuBufferSlice nativeVertices;
    private ByteBuffer nativeUpload;

    public GpuBufferSlice nativeVertices() {
        return nativeVertices;
    }

    public int nativeCount() {
        return vertexCount;
    }
    //? }

    public boolean build(Level level, double lx, double ly, double lz, float range,
                         int anchorX, int anchorY, int anchorZ, boolean models, float partialTick) {
        long sig = gather(level, lx, ly, lz, range, partialTick);
        if (sig == 0L) return false;
        sig = mix(sig, anchorX);
        sig = mix(sig, anchorY);
        sig = mix(sig, anchorZ);
        sig = mix(sig, models ? 1 : 0);
        if (sig == builtSig && vertexCount > 0) {
            entityCount = found.size();
            return true;
        }
        boolean built = build(anchorX, anchorY, anchorZ, models, partialTick);
        builtSig = built ? sig : 0L;
        return built;
    }

    public long gather(Level level, double lx, double ly, double lz, float range, float partialTick) {
        found.clear();
        if (level == null) return 0L;
        AABB area = new AABB(lx - range, ly - range, lz - range, lx + range, ly + range, lz + range);
        List<Entity> entities;
        try {
            entities = level.getEntities((Entity) null, area, e -> !e.isSpectator() && ShadowSettings.defaults().castsShadow(e));
        } catch (Throwable t) {
            return 0L;
        }
        float r2 = range * range;
        long h = FNV_OFFSET;
        for (Entity e : entities) {
            if (!within(e, lx, ly, lz, r2)) continue;
            found.add(e);
            h = mix(h, e.getId());
            h = mix(h, quantize(e.getX()));
            h = mix(h, quantize(e.getY()));
            h = mix(h, quantize(e.getZ()));
            h = mix(h, Float.floatToIntBits(e.getYRot()));
            h = mix(h, Float.floatToIntBits(e.getXRot()));
            h = mix(h, e.getPose().ordinal());
            if (e instanceof LivingEntity living) {
                h = mix(h, Float.floatToIntBits(living.yBodyRot));
                h = mix(h, Float.floatToIntBits(living.yHeadRot));
                h = mix(h, quantize(living.walkAnimation.position(partialTick)));
            }
        }
        return found.isEmpty() ? 0L : (h == 0L ? 1L : h);
    }

    public boolean build(int anchorX, int anchorY, int anchorZ, boolean models, float partialTick) {
        builtSig = 0L;
        vertexCount = 0;
        entityCount = 0;
        triFloats = 0;
        if (found.isEmpty()) return false;

        for (Entity e : found) {
            boolean captured = false;
            if (models) {
                capture.reset();
                if (EntityModelCapture.capture(e, partialTick, anchorX, anchorY, anchorZ, capture)) {
                    appendCapturedQuads();
                    captured = true;
                }
            }
            if (!captured) appendFallbackBox(e, anchorX, anchorY, anchorZ);
            entityCount++;
        }
        if (triFloats == 0) return false;

        //? if >=26.2 {
        int length = triFloats * Float.BYTES;
        if (nativeUpload == null || nativeUpload.capacity() < length) {
            if (nativeUpload != null) MemoryUtil.memFree(nativeUpload);
            nativeUpload = MemoryUtil.memAlloc(Math.max(length, tri.length * Float.BYTES));
        }
        var bytes = nativeUpload.clear().limit(length);
        bytes.asFloatBuffer().put(tri, 0, triFloats);
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        nativeVertices = encoder.transientMemory().uploadGpu(bytes, Float.BYTES, GpuBuffer.USAGE_VERTEX);
        encoder.submit();
        //? } else {
        /*if (upload == null || upload.capacity() < triFloats) {
            if (upload != null) MemoryUtil.memFree(upload);
            upload = MemoryUtil.memAllocFloat(Math.max(triFloats, tri.length));
        }
        FloatBuffer buf = upload;
        buf.clear();
        buf.put(tri, 0, triFloats).flip();
        if (vao == 0) {
            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, FLOATS_PER_VERTEX, GL11.GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 0L);
            GlState.resetVertexArray();
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        *///? }
        vertexCount = triFloats / FLOATS_PER_VERTEX;
        return vertexCount > 0;
    }

    private void appendFallbackBox(Entity e, int anchorX, int anchorY, int anchorZ) {
        AABB bb = e.getBoundingBox();
        appendBox((float) (bb.minX - anchorX), (float) (bb.minY - anchorY), (float) (bb.minZ - anchorZ),
                (float) (bb.maxX - anchorX), (float) (bb.maxY - anchorY), (float) (bb.maxZ - anchorZ));
    }

    public boolean hasGeometry() {
        return vertexCount > 0;
    }

    public int boxCount() {
        return entityCount;
    }

    public void draw() {
        if (vertexCount == 0) return;
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        GlState.resetVertexArray();
    }

    public void dispose() {
        //? if >=26.2 {
        nativeVertices = null;
        if (nativeUpload != null) {
            MemoryUtil.memFree(nativeUpload);
            nativeUpload = null;
        }
        //? }
        if (vbo != 0) {
            GL15.glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (upload != null) {
            MemoryUtil.memFree(upload);
            upload = null;
        }
        found.clear();
        vertexCount = 0;
        builtSig = 0L;
    }

    private static long mix(long h, long v) {
        return (h ^ v) * FNV_PRIME;
    }

    private static long quantize(double v) {
        return Math.round(v * POSITION_STEPS);
    }

    private void appendCapturedQuads() {
        float[] v = capture.data();
        int verts = capture.vertexCount();
        for (int q = 0; q + 3 < verts; q += 4) {
            int a = q * FLOATS_PER_VERTEX;
            int b = (q + 1) * FLOATS_PER_VERTEX;
            int c = (q + 2) * FLOATS_PER_VERTEX;
            int d = (q + 3) * FLOATS_PER_VERTEX;
            pushVertex(v, a);
            pushVertex(v, b);
            pushVertex(v, c);
            pushVertex(v, a);
            pushVertex(v, c);
            pushVertex(v, d);
        }
    }

    private void pushVertex(float[] data, int at) {
        push(data[at], data[at + 1], data[at + 2]);
    }

    private void appendBox(float x1, float y1, float z1, float x2, float y2, float z2) {
        quad(x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
        quad(x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2);
        quad(x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2);
        quad(x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        quad(x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1);
        quad(x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
    }

    private void quad(float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz) {
        push(ax, ay, az);
        push(bx, by, bz);
        push(cx, cy, cz);
        push(ax, ay, az);
        push(cx, cy, cz);
        push(dx, dy, dz);
    }

    private void push(float x, float y, float z) {
        if (triFloats + FLOATS_PER_VERTEX > tri.length) {
            float[] grown = new float[tri.length * 2];
            System.arraycopy(tri, 0, grown, 0, triFloats);
            tri = grown;
        }
        tri[triFloats] = x;
        tri[triFloats + 1] = y;
        tri[triFloats + 2] = z;
        triFloats += FLOATS_PER_VERTEX;
    }

    private static boolean within(Entity e, double lx, double ly, double lz, float r2) {
        AABB bb = e.getBoundingBox();
        double dx = (bb.minX + bb.maxX) * 0.5 - lx;
        double dy = (bb.minY + bb.maxY) * 0.5 - ly;
        double dz = (bb.minZ + bb.maxZ) * 0.5 - lz;
        return dx * dx + dy * dy + dz * dz <= r2;
    }
}
