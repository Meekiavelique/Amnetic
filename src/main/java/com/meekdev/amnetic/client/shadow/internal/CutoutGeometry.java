package com.meekdev.amnetic.client.shadow.internal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

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

    private static final Direction[] FACES_PLUS_NULL = new Direction[Direction.values().length + 1];
    static {
        Direction[] dirs = Direction.values();
        System.arraycopy(dirs, 0, FACES_PLUS_NULL, 0, dirs.length);
        FACES_PLUS_NULL[dirs.length] = null;
    }

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
        BlockStateModel model;
        try {
            model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        } catch (Throwable t) {
            return null;
        }
        if (model == null) return null;

        List<BlockStateModelPart> parts = new ArrayList<>(4);
        try {
            model.collectParts(RandomSource.create(42L), parts);
        } catch (Throwable t) {
            return null;
        }

        List<Float> cutout = new ArrayList<>();
        List<Float> translucent = new ArrayList<>();
        boolean hasSolid = false;
        for (BlockStateModelPart part : parts) {
            for (Direction dir : FACES_PLUS_NULL) {
                List<BakedQuad> quads;
                try {
                    quads = part.getQuads(dir);
                } catch (Throwable t) {
                    continue;
                }
                for (BakedQuad q : quads) {
                    ChunkSectionLayer layer = q.materialInfo().layer();
                    if (layer == ChunkSectionLayer.SOLID) hasSolid = true;
                    else if (layer == ChunkSectionLayer.CUTOUT) emitQuad(cutout, q);
                    else if (layer == ChunkSectionLayer.TRANSLUCENT) emitQuad(translucent, q);
                }
            }
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

    private static void emitQuad(List<Float> out, BakedQuad q) {
        vertex(out, q, 0); vertex(out, q, 1); vertex(out, q, 2);
        vertex(out, q, 0); vertex(out, q, 2); vertex(out, q, 3);
    }

    private static void vertex(List<Float> out, BakedQuad q, int i) {
        Vector3fc p = q.position(i);
        long uv = q.packedUV(i);
        out.add(p.x()); out.add(p.y()); out.add(p.z());
        out.add(Float.intBitsToFloat((int) (uv >>> 32))); // u
        out.add(Float.intBitsToFloat((int) (uv & 0xFFFFFFFFL))); // v
    }
}
