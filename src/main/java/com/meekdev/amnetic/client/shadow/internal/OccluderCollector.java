package com.meekdev.amnetic.client.shadow.internal;

import com.meekdev.amnetic.client.shadow.ShadowShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;

public final class OccluderCollector {

    private record Section(WeakReference<Object> chunk, List<OccluderEntry> entries) {}
    private static final Map<Long, Section> SECTIONS = new LinkedHashMap<>(64, .75f, true);
    private static final int MAX_SECTIONS = 384;
    private static Level cachedLevel;

    public static void clear() {
        SECTIONS.clear();
        cachedLevel = null;
    }

    public static void invalidateAt(BlockPos pos) {
        for (int x = (pos.getX() - 1) >> 4; x <= (pos.getX() + 1) >> 4; x++) {
            for (int y = (pos.getY() - 1) >> 4; y <= (pos.getY() + 1) >> 4; y++) {
                for (int z = (pos.getZ() - 1) >> 4; z <= (pos.getZ() + 1) >> 4; z++) {
                    SECTIONS.remove(SectionPos.asLong(x, y, z));
                }
            }
        }
    }

    private OccluderCollector() {}

    public static List<OccluderEntry> collectForLight(Level level,
                                                      float lx, float ly, float lz,
                                                      float radius,
                                                      int hostX, int hostY, int hostZ) {
        List<OccluderEntry> out = new ArrayList<>();
        if (level == null || radius < 1e-3f) return out;
        if (cachedLevel != level) {
            clear();
            cachedLevel = level;
        }

        float r2 = radius * radius;
        int minY = Math.max(level.getMinY(), (int) Math.floor(ly - radius));
        int maxY = Math.min(level.getMaxY(), (int) Math.floor(ly + radius));
        int minSx = ((int) Math.floor(lx - radius)) >> 4;
        int maxSx = ((int) Math.floor(lx + radius)) >> 4;
        int minSz = ((int) Math.floor(lz - radius)) >> 4;
        int maxSz = ((int) Math.floor(lz + radius)) >> 4;
        for (int sx = minSx; sx <= maxSx; sx++) {
            for (int sz = minSz; sz <= maxSz; sz++) {
                var chunk = level.getChunk(sx, sz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    long key = SectionPos.asLong(sx, sy, sz);
                    var section = chunk.getSection(chunk.getSectionIndex(sy << 4));
                    if (section.hasOnlyAir()) {
                        SECTIONS.remove(key);
                        continue;
                    }
                    Section cached = cachedSection(level, chunk, key, sx, sy, sz, section);
                    for (OccluderEntry entry : cached.entries) {
                        BlockPos pos = entry.pos;
                        float dx = pos.getX() + .5f - lx;
                        float dy = pos.getY() + .5f - ly;
                        float dz = pos.getZ() + .5f - lz;
                        if (dx * dx + dy * dy + dz * dz > r2) continue;
                        var state = chunk.getBlockState(pos);
                        boolean host = pos.getX() == hostX && pos.getY() == hostY && pos.getZ() == hostZ;
                        if (host && state.getLightEmission() > 0) continue;
                        var provider = ShadowShapes.provider(state);
                        if (provider == null) {
                            out.add(entry);
                        } else if (!ShadowShapes.isTracked(level, pos)) {
                            out.add(new OccluderEntry(pos, provider.shape(level, pos, state)));
                        }
                    }
                }
            }
        }
        return out;
    }

    private static Section cachedSection(Level level, ChunkAccess chunk, long key, int sx, int sy, int sz,
                                         LevelChunkSection section) {
        Section cached = SECTIONS.get(key);
        if (cached != null && cached.chunk.get() == chunk) return cached;
        cached = new Section(new WeakReference<>(chunk), collectSection(level, sx, sy, sz, section));
        SECTIONS.put(key, cached);
        while (SECTIONS.size() > MAX_SECTIONS) SECTIONS.remove(SECTIONS.keySet().iterator().next());
        return cached;
    }

    private static List<OccluderEntry> collectSection(Level level, int sx, int sy, int sz,
                                                    LevelChunkSection section) {
        List<OccluderEntry> out = new ArrayList<>();

        int minX = sx << 4;
        int minY = sy << 4;
        int minZ = sz << 4;
        int maxX = minX + 15;
        int maxY = minY + 15;
        int maxZ = minZ + 15;

        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {

                    mut.set(x, y, z);
                    BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                    if (state.isAir()) continue;

                    var dynamic = ShadowShapes.provider(state);
                    if (dynamic != null) {
                        out.add(new OccluderEntry(mut.immutable(), dynamic.shape(level, mut, state)));
                        continue;
                    }
                    if (state.getRenderShape() == RenderShape.INVISIBLE && !state.hasBlockEntity()) continue;

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
