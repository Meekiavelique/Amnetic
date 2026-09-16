package com.meekdev.amnetic.client.shadow.internal;

import com.meekdev.amnetic.client.compat.BlockQuads;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class CutoutGeometry {

    public static final class Result {
        public final float[] cutout;
        public final float[] translucent;
        Result(float[] cutout, float[] translucent) { this.cutout = cutout; this.translucent = translucent; }
        boolean empty() { return cutout == null && translucent == null; }
    }

    private static final Result NONE = new Result(null, null);
    private static final Map<BlockState, Result> CACHE = new IdentityHashMap<>();

    private CutoutGeometry() {}

    public static void clear() { CACHE.clear(); }

    public static Result extract(BlockState state) {
        Result cached = CACHE.get(state);
        if (cached != null) return cached == NONE ? null : cached;
        Result result = compute(state);
        CACHE.put(state, result == null ? NONE : result);
        return result;
    }

    private static Result compute(BlockState state) {
        List<BlockQuads.Quad> quads = BlockQuads.of(state);
        if (quads == null) return null;

        List<Float> cutout = new ArrayList<>();
        List<Float> translucent = new ArrayList<>();
        boolean hasSolid = false;
        for (BlockQuads.Quad q : quads) {
            if (q.layer() == BlockQuads.Layer.SOLID) hasSolid = true;
            else if (q.layer() == BlockQuads.Layer.CUTOUT) emitQuad(cutout, q);
            else if (q.layer() == BlockQuads.Layer.TRANSLUCENT) emitQuad(translucent, q);
        }
        if (hasSolid) return null;
        Result r = new Result(toArray(cutout), toArray(translucent));
        return r.empty() ? null : r;
    }

    private static float[] toArray(List<Float> list) {
        if (list.isEmpty()) return null;
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    private static void emitQuad(List<Float> out, BlockQuads.Quad q) {
        vertex(out, q, 0); vertex(out, q, 1); vertex(out, q, 2);
        vertex(out, q, 0); vertex(out, q, 2); vertex(out, q, 3);
    }

    private static void vertex(List<Float> out, BlockQuads.Quad q, int i) {
        out.add(q.x(i)); out.add(q.y(i)); out.add(q.z(i));
        out.add(q.u(i)); out.add(q.v(i));
    }
}
