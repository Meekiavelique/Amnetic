package com.meekdev.amnetic.client.emissive.internal;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

public final class BlockEmissiveGeometry {

    private static final float[] NONE = new float[0];
    private static final Map<BlockState, float[]> CACHE = new IdentityHashMap<>();

    private static final Direction[] FACES_PLUS_NULL = new Direction[Direction.values().length + 1];

    static {
        Direction[] dirs = Direction.values();
        System.arraycopy(dirs, 0, FACES_PLUS_NULL, 0, dirs.length);
        FACES_PLUS_NULL[dirs.length] = null;
    }

    private BlockEmissiveGeometry() {}

    public static void clear() {
        CACHE.clear();
    }

    public static float[] quads(BlockState state) {
        float[] cached = CACHE.get(state);
        if (cached != null) return cached == NONE ? null : cached;
        float[] built = compute(state);
        CACHE.put(state, built == null ? NONE : built);
        return built;
    }

    private static float[] compute(BlockState state) {
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

        List<Float> out = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction dir : FACES_PLUS_NULL) {
                List<BakedQuad> quads;
                try {
                    quads = part.getQuads(dir);
                } catch (Throwable t) {
                    continue;
                }
                for (BakedQuad q : quads) {
                    emitQuad(out, q);
                }
            }
        }
        if (out.isEmpty()) return null;
        float[] arr = new float[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
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
        out.add(Float.intBitsToFloat((int) (uv >>> 32)));
        out.add(Float.intBitsToFloat((int) (uv & 0xFFFFFFFFL)));
    }
}
