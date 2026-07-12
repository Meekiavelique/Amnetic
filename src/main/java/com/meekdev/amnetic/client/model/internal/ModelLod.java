package com.meekdev.amnetic.client.model.internal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * pregenerates LOD index lists for a model's parts on a low-priority background thread via
 * MeshSimplifier, so it never stalls the render/load thread. each qualifying part gets a ladder
 * of coarser index lists published into ModelIR.Part.lods, the render loop picks a level by distance
 */
public final class ModelLod {

    private static final int MIN_TRIS = 1500; // below this, not worth simplifying
    private static final float[] RATIOS = { 0.45f, 0.18f, 0.06f }; // LOD 1..3 as a fraction of the original

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Amnetic-LOD");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private ModelLod() {}

    // number of LOD levels, level 0 = full detail
    public static int levels() {
        return RATIOS.length + 1;
    }

    // queues background LOD generation for every large non-skinned indexed part
    public static void generate(ModelIR ir) {
        POOL.submit(() -> {
            for (ModelIR.Part p : ir.parts()) {
                try {
                    if (p.hasSkin() || !p.hasIndices()) {
                        continue;
                    }
                    int tris = p.indices.length / 3;
                    if (tris < MIN_TRIS) {
                        continue;
                    }
                    int[][] lods = new int[RATIOS.length + 1][];
                    lods[0] = p.indices;
                    for (int i = 0; i < RATIOS.length; i++) {
                        int target = Math.max(8, (int) (tris * RATIOS[i]));
                        lods[i + 1] = MeshSimplifier.simplify(p.vertices, p.indices, target);
                    }
                    p.lods = lods; // volatile publish, the render thread picks it up on the next draw
                } catch (Throwable ignored) {
                    // a failed simplification just leaves this part at full detail, never crash the pool
                }
            }
        });
    }
}
