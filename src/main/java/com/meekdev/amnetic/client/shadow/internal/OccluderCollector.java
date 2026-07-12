package com.meekdev.amnetic.client.shadow.internal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
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

        for (int x = minX; x <= maxX; x++) {
            float dx = (x + 0.5f) - lx;
            float dx2 = dx * dx;
            if (dx2 > r2) continue;
            for (int z = minZ; z <= maxZ; z++) {
                float dz = (z + 0.5f) - lz;
                float dxz2 = dx2 + dz * dz;
                if (dxz2 > r2) continue;
                for (int y = minY; y <= maxY; y++) {
                    float dy = (y + 0.5f) - ly;
                    if (dxz2 + dy * dy > r2) continue;

                    mut.set(x, y, z);
                    if (!level.hasChunkAt(mut)) continue; // unloaded -> no false shadow, no load
                    BlockState state = level.getBlockState(mut);
                    if (state.isAir()) continue;

                    if (x == hostX && y == hostY && z == hostZ && state.getLightEmission() > 0) continue;

                    if (state.getRenderShape() == RenderShape.INVISIBLE) continue;

                    CutoutGeometry.Result quads = CutoutGeometry.extract(state);
                    if (quads != null) {
                        out.add(new OccluderEntry(mut.immutable(), quads.cutout, quads.translucent));
                        continue;
                    }

                    VoxelShape shape;
                    try {
                        shape = state.getCollisionShape(level, mut);
                        if (shape == null || shape.isEmpty()) shape = state.getShape(level, mut);
                    } catch (Throwable t) {
                        continue;
                    }
                    if (shape == null || shape.isEmpty()) continue;

                    out.add(new OccluderEntry(mut.immutable(), shape));
                }
            }
        }
        return out;
    }
}
