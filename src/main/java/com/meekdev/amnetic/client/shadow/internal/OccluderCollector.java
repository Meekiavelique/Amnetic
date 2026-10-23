package com.meekdev.amnetic.client.shadow.internal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;


public final class OccluderCollector {

    private OccluderCollector() {}

    public static List<OccluderEntry> collectForLight(Level level,
                                                      float lx, float ly, float lz,
                                                      float radius,
                                                      int hostX, int hostY, int hostZ) {
        List<OccluderEntry> out = new ArrayList<>();
        if (level == null || radius < 1e-3f) return out;

        int minX = (int) Math.floor(lx - radius);
        int minY = (int) Math.floor(ly - radius);
        int minZ = (int) Math.floor(lz - radius);
        int maxX = (int) Math.floor(lx + radius);
        int maxY = (int) Math.floor(ly + radius);
        int maxZ = (int) Math.floor(lz + radius);

        float r2 = radius * radius;
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        //? if >=1.21.2 {
        int minSectionY = level.getMinSectionY();
        //?} else {
        /*int minSectionY = level.getMinSection();
        *///?}

        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    int si = sy - minSectionY;
                    if (si < 0 || si >= sections.length) continue;
                    LevelChunkSection section = sections[si];
                    if (section == null || section.hasOnlyAir()) continue;
                    int x0 = Math.max(minX, cx << 4), x1 = Math.min(maxX, (cx << 4) + 15);
                    int y0 = Math.max(minY, sy << 4), y1 = Math.min(maxY, (sy << 4) + 15);
                    int z0 = Math.max(minZ, cz << 4), z1 = Math.min(maxZ, (cz << 4) + 15);
                    for (int x = x0; x <= x1; x++) {
                        float dx = (x + 0.5f) - lx;
                        float dx2 = dx * dx;
                        if (dx2 > r2) continue;
                        for (int z = z0; z <= z1; z++) {
                            float dz = (z + 0.5f) - lz;
                            float dxz2 = dx2 + dz * dz;
                            if (dxz2 > r2) continue;
                            for (int y = y0; y <= y1; y++) {
                                float dy = (y + 0.5f) - ly;
                                if (dxz2 + dy * dy > r2) continue;

                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                if (state.isAir()) continue;
                                mut.set(x, y, z);
                                if (x == hostX && y == hostY && z == hostZ && state.getLightEmission() > 0) continue;
                                collect(level, state, mut, out);
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    public static void collect(Level level, BlockState state, BlockPos.MutableBlockPos at, List<OccluderEntry> out) {
        if (state.getRenderShape() == RenderShape.INVISIBLE) return;
        CutoutGeometry.Result quads = CutoutGeometry.extract(state);
        if (quads != null) {
            out.add(new OccluderEntry(at.immutable(), quads.cutout, quads.translucent));
            return;
        }
        VoxelShape shape;
        boolean full;
        try {
            shape = state.getCollisionShape(level, at);
            full = !shape.isEmpty() && state.isCollisionShapeFullBlock(level, at);
            if (shape.isEmpty()) shape = state.getShape(level, at);
        } catch (Throwable t) {
            return;
        }
        if (shape == null || shape.isEmpty()) return;
        out.add(new OccluderEntry(at.immutable(), shape, full));
    }

    public static boolean solid(Level level, BlockState state, BlockPos at) {
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE || CutoutGeometry.extract(state) != null) return false;
        try {
            return state.isCollisionShapeFullBlock(level, at);
        } catch (Throwable t) {
            return false;
        }
    }
}
