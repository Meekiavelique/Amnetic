package com.meekdev.amnetic.client.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
//? if >=26.1 {
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Vector3fc;
//?} else if >=1.21.5 {
/*import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.joml.Vector3fc;
*///?} else {
/*import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
*///?}

public final class BlockQuads {

    public enum Layer { SOLID, CUTOUT, TRANSLUCENT, OTHER }

    public record Quad(float[] positions, float[] uvs, TextureAtlasSprite sprite, int lightEmission, Layer layer) {
        public float x(int corner) { return positions[corner * 3]; }
        public float y(int corner) { return positions[corner * 3 + 1]; }
        public float z(int corner) { return positions[corner * 3 + 2]; }
        public float u(int corner) { return uvs[corner * 2]; }
        public float v(int corner) { return uvs[corner * 2 + 1]; }
    }

    private static final Direction[] FACES_PLUS_NULL = new Direction[Direction.values().length + 1];

    static {
        Direction[] dirs = Direction.values();
        System.arraycopy(dirs, 0, FACES_PLUS_NULL, 0, dirs.length);
        FACES_PLUS_NULL[dirs.length] = null;
    }

    private BlockQuads() {}

    public static List<Quad> of(BlockState state) {
        List<Quad> out = new ArrayList<>();
        //? if >=26.1 {
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
        for (var part : parts) {
            for (Direction dir : FACES_PLUS_NULL) {
                List<BakedQuad> quads;
                try {
                    quads = part.getQuads(dir);
                } catch (Throwable t) {
                    continue;
                }
                for (BakedQuad q : quads) {
                    float[] positions = new float[12];
                    float[] uvs = new float[8];
                    for (int i = 0; i < 4; i++) {
                        Vector3fc p = q.position(i);
                        long uv = q.packedUV(i);
                        positions[i * 3] = p.x();
                        positions[i * 3 + 1] = p.y();
                        positions[i * 3 + 2] = p.z();
                        uvs[i * 2] = Float.intBitsToFloat((int) (uv >>> 32));
                        uvs[i * 2 + 1] = Float.intBitsToFloat((int) (uv & 0xFFFFFFFFL));
                    }
                    BakedQuad.MaterialInfo info = q.materialInfo();
                    out.add(new Quad(positions, uvs, info.sprite(), info.lightEmission(), layer(info.layer())));
                }
            }
        }
        //?} else if >=1.21.5 {
        /*BlockStateModel model;
        try {
            model = Minecraft.getInstance().getModelManager().getBlockModelShaper().getBlockModel(state);
        } catch (Throwable t) {
            return null;
        }
        if (model == null) return null;
        List<BlockModelPart> parts = new ArrayList<>(4);
        try {
            model.collectParts(RandomSource.create(42L), parts);
        } catch (Throwable t) {
            return null;
        }
        Layer blockLayer = layer(ItemBlockRenderTypes.getChunkRenderType(state));
        for (var part : parts) {
            for (Direction dir : FACES_PLUS_NULL) {
                List<BakedQuad> quads;
                try {
                    quads = part.getQuads(dir);
                } catch (Throwable t) {
                    continue;
                }
                for (BakedQuad q : quads) {
                    float[] positions = new float[12];
                    float[] uvs = new float[8];
                    for (int i = 0; i < 4; i++) {
                        Vector3fc p = q.position(i);
                        long uv = q.packedUV(i);
                        positions[i * 3] = p.x();
                        positions[i * 3 + 1] = p.y();
                        positions[i * 3 + 2] = p.z();
                        uvs[i * 2] = Float.intBitsToFloat((int) (uv >>> 32));
                        uvs[i * 2 + 1] = Float.intBitsToFloat((int) (uv & 0xFFFFFFFFL));
                    }
                    out.add(new Quad(positions, uvs, q.sprite(), q.lightEmission(), blockLayer));
                }
            }
        }
        *///?} else {
        /*BakedModel model;
        try {
            model = Minecraft.getInstance().getModelManager().getBlockModelShaper().getBlockModel(state);
        } catch (Throwable t) {
            return null;
        }
        if (model == null) return null;
        Layer blockLayer = layer(ItemBlockRenderTypes.getChunkRenderType(state));
        int stride = DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;
        for (Direction dir : FACES_PLUS_NULL) {
            List<BakedQuad> quads;
            try {
                quads = model.getQuads(state, dir, RandomSource.create(42L));
            } catch (Throwable t) {
                continue;
            }
            for (BakedQuad q : quads) {
                int[] v = q.getVertices();
                float[] positions = new float[12];
                float[] uvs = new float[8];
                for (int i = 0; i < 4; i++) {
                    int o = i * stride;
                    positions[i * 3] = Float.intBitsToFloat(v[o]);
                    positions[i * 3 + 1] = Float.intBitsToFloat(v[o + 1]);
                    positions[i * 3 + 2] = Float.intBitsToFloat(v[o + 2]);
                    uvs[i * 2] = Float.intBitsToFloat(v[o + 4]);
                    uvs[i * 2 + 1] = Float.intBitsToFloat(v[o + 5]);
                }
                out.add(new Quad(positions, uvs, q.getSprite(), 0, blockLayer));
            }
        }
        *///?}
        return out;
    }

    //? if >=1.21.5 {
    private static Layer layer(ChunkSectionLayer layer) {
        if (layer == ChunkSectionLayer.SOLID) return Layer.SOLID;
        if (layer == ChunkSectionLayer.CUTOUT) return Layer.CUTOUT;
        if (layer == ChunkSectionLayer.TRANSLUCENT) return Layer.TRANSLUCENT;
        return Layer.OTHER;
    }
    //?} else {
    /*private static Layer layer(RenderType type) {
        if (type == RenderType.solid()) return Layer.SOLID;
        if (type == RenderType.cutout() || type == RenderType.cutoutMipped()) return Layer.CUTOUT;
        if (type == RenderType.translucent()) return Layer.TRANSLUCENT;
        return Layer.OTHER;
    }
    *///?}
}
