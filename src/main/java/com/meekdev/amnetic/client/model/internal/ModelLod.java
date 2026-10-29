package com.meekdev.amnetic.client.model.internal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class ModelLod {
    public static final int LEVELS = 4;
    private static final int MIN_MODEL_TRIANGLES = 256;
    private static final int MIN_PART_TRIANGLES = 64;
    private static final float[] RATIOS = {0.5f, 0.25f, 0.1f};
    private static final float[] MOST_ERROR = {0.02f, 0.06f, 0.2f};
    private static final float WORTH = 0.85f;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Amnetic-LOD");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private ModelLod() {}

    public static int levels() {
        return LEVELS;
    }

    public static void generate(ModelIR ir) {
        POOL.submit(() -> {
            try {
                build(ir);
            } catch (Throwable ignored) {
            }
        });
    }

    public static void build(ModelIR ir) {
        int total = 0;
        for (ModelIR.Part part : ir.parts()) {
            if (part.hasIndices() && !part.hasSkin()) total += part.indices.length / 3;
        }
        if (total < MIN_MODEL_TRIANGLES) return;
        float radius = radius(ir);
        if (radius <= 0) return;
        for (ModelIR.Part part : ir.parts()) {
            if (part.hasSkin() || !part.hasIndices() || part.lods != null) continue;
            int triangles = part.indices.length / 3;
            if (triangles < MIN_PART_TRIANGLES) continue;
            float scale = scale(part.transform);
            int[] targets = new int[RATIOS.length];
            float[] most = new float[RATIOS.length];
            for (int n = 0; n < RATIOS.length; n++) {
                targets[n] = Math.max(4, (int) (triangles * RATIOS[n]));
                most[n] = radius * MOST_ERROR[n] / scale;
            }
            MeshSimplifier.Level[] made = MeshSimplifier.levels(part.vertices, part.indices, targets, most);
            int[][] lods = new int[LEVELS][];
            float[] errors = new float[LEVELS];
            lods[0] = part.indices;
            for (int n = 1; n < LEVELS; n++) {
                MeshSimplifier.Level level = made[n - 1];
                boolean worth = level.indices().length < lods[n - 1].length * WORTH;
                lods[n] = worth ? level.indices() : lods[n - 1];
                errors[n] = worth ? level.error() : errors[n - 1];
            }
            part.lodErrors = errors;
            part.lods = lods;
        }
    }

    private static float radius(ModelIR ir) {
        Vector3f min = new Vector3f(Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        Vector3f v = new Vector3f();
        int stride = ModelIR.VERTEX_STRIDE_FLOATS;
        for (ModelIR.Part part : ir.parts()) {
            if (part.hasSkin()) continue;
            float[] verts = part.vertices;
            for (int i = 0; i + 2 < verts.length; i += stride) {
                part.transform.transformPosition(v.set(verts[i], verts[i + 1], verts[i + 2]));
                min.min(v);
                max.max(v);
            }
        }
        return min.x > max.x ? 0 : max.sub(min).length() / 2;
    }

    public static final float COARSER = 0.7f;

    public static int pick(float[] errors, float perUnit, float pixels, int current) {
        if (errors == null || errors.length <= 1 || pixels <= 0) return 0;
        int ideal = 0;
        for (int level = errors.length - 1; level > 0; level--) {
            if (errors[level] * perUnit <= pixels) {
                ideal = level;
                break;
            }
        }
        current = Math.min(current, errors.length - 1);
        if (current < 0 || ideal <= current) return ideal;
        for (int level = ideal; level > current; level--) {
            if (errors[level] * perUnit <= pixels * COARSER) return level;
        }
        return current;
    }

    static float scale(Matrix4f transform) {
        Vector3f axes = transform.getScale(new Vector3f());
        float most = Math.max(axes.x, Math.max(axes.y, axes.z));
        return most > 1e-6f ? most : 1f;
    }
}
