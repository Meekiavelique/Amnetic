package com.meekdev.amnetic.client.emissive.internal;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

public final class BlockEmissiveGeometry {

    public static final int FLOATS_PER_VERTEX = 9;

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

        float blockEmission = state.getLightEmission() / 15f;
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
                    BakedQuad.MaterialInfo info = q.materialInfo();
                    float quadEmission = info.lightEmission() > 0
                            ? info.lightEmission() / 15f
                            : blockEmission;
                    if (quadEmission <= 0f) continue;

                    TextureAtlasSprite mask = maskFor(info.sprite());
                    emitQuad(out, q, info.sprite(), mask, quadEmission);
                }
            }
        }
        if (out.isEmpty()) return null;
        float[] arr = new float[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    private static TextureAtlasSprite maskFor(TextureAtlasSprite sprite) {
        if (sprite == null) return null;
        Identifier name = sprite.contents().name();
        Identifier maskId = Identifier.fromNamespaceAndPath(name.getNamespace(), name.getPath() + "_e");
        try {
            TextureAtlas atlas = Minecraft.getInstance().getAtlasManager()
                    .getAtlasOrThrow(TextureAtlas.LOCATION_BLOCKS);
            TextureAtlasSprite found = atlas.getSprite(maskId);
            if (found == null) return null;
            return maskId.equals(found.contents().name()) ? found : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void emitQuad(List<Float> out, BakedQuad q, TextureAtlasSprite sprite,
                                 TextureAtlasSprite mask, float emission) {
        vertex(out, q, 0, sprite, mask, emission);
        vertex(out, q, 1, sprite, mask, emission);
        vertex(out, q, 2, sprite, mask, emission);
        vertex(out, q, 0, sprite, mask, emission);
        vertex(out, q, 2, sprite, mask, emission);
        vertex(out, q, 3, sprite, mask, emission);
    }

    private static void vertex(List<Float> out, BakedQuad q, int i, TextureAtlasSprite sprite,
                               TextureAtlasSprite mask, float emission) {
        Vector3fc p = q.position(i);
        long uv = q.packedUV(i);
        float u = Float.intBitsToFloat((int) (uv >>> 32));
        float v = Float.intBitsToFloat((int) (uv & 0xFFFFFFFFL));

        float mu = u;
        float mv = v;
        float hasMask = 0f;
        if (mask != null && sprite != null) {
            float su0 = sprite.getU0(), su1 = sprite.getU1();
            float sv0 = sprite.getV0(), sv1 = sprite.getV1();
            float du = su1 - su0, dv = sv1 - sv0;
            if (Math.abs(du) > 1e-9f && Math.abs(dv) > 1e-9f) {
                float lu = (u - su0) / du;
                float lv = (v - sv0) / dv;
                mu = mask.getU0() + lu * (mask.getU1() - mask.getU0());
                mv = mask.getV0() + lv * (mask.getV1() - mask.getV0());
                hasMask = 1f;
            }
        }

        out.add(p.x()); out.add(p.y()); out.add(p.z());
        out.add(u); out.add(v);
        out.add(mu); out.add(mv);
        out.add(emission); out.add(hasMask);
    }
}
