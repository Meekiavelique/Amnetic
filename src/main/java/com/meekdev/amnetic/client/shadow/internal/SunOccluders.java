package com.meekdev.amnetic.client.shadow.internal;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

final class SunOccluders {

    private static final long BUDGET_NANOS = 2_000_000L;
    private static final LongOpenHashSet DIRTY = new LongOpenHashSet();

    private final Long2ObjectOpenHashMap<OccluderMesh> built = new Long2ObjectOpenHashMap<>();
    private final LongArrayList wanted = new LongArrayList();
    private final LongOpenHashSet wantedSet = new LongOpenHashSet();
    private final List<OccluderMesh> drawn = new ArrayList<>();
    private final List<OccluderEntry> scratch = new ArrayList<>();
    private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private Level level;

    static void blockChanged(BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        int sx = x >> 4, sy = y >> 4, sz = z >> 4;
        DIRTY.add(key(sx, sy, sz));
        if ((x & 15) == 0) DIRTY.add(key(sx - 1, sy, sz));
        if ((x & 15) == 15) DIRTY.add(key(sx + 1, sy, sz));
        if ((y & 15) == 0) DIRTY.add(key(sx, sy - 1, sz));
        if ((y & 15) == 15) DIRTY.add(key(sx, sy + 1, sz));
        if ((z & 15) == 0) DIRTY.add(key(sx, sy, sz - 1));
        if ((z & 15) == 15) DIRTY.add(key(sx, sy, sz + 1));
    }

    List<OccluderMesh> update(Level level, float cx, float cy, float cz, float r) {
        if (level != this.level) {
            clear();
            this.level = level;
        }
        wanted.clear();
        wantedSet.clear();
        int minSy = minSection();
        int x0 = (int) Math.floor(cx - r) >> 4, x1 = (int) Math.floor(cx + r) >> 4;
        int y0 = Math.max((int) Math.floor(cy - r) >> 4, minSy), y1 = (int) Math.floor(cy + r) >> 4;
        int z0 = (int) Math.floor(cz - r) >> 4, z1 = (int) Math.floor(cz + r) >> 4;
        for (int sx = x0; sx <= x1; sx++) {
            for (int sz = z0; sz <= z1; sz++) {
                for (int sy = y0; sy <= y1; sy++) {
                    if (gap(cx, sx) + gap(cy, sy) + gap(cz, sz) > r * r) continue;
                    long k = key(sx, sy, sz);
                    wanted.add(k);
                    wantedSet.add(k);
                }
            }
        }
        wanted.sort((a, b) -> Float.compare(distance(a, cx, cy, cz), distance(b, cx, cy, cz)));

        var it = built.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            if (wantedSet.contains(e.getLongKey())) continue;
            if (e.getValue() != null) e.getValue().close();
            it.remove();
        }

        boolean cold = built.isEmpty();
        long start = System.nanoTime();
        for (int i = 0; i < wanted.size(); i++) {
            long k = wanted.getLong(i);
            if (built.containsKey(k) && !DIRTY.contains(k)) continue;
            if (!cold && System.nanoTime() - start > BUDGET_NANOS) break;
            int sx = sectionX(k), sy = sectionY(k), sz = sectionZ(k);
            ChunkAccess chunk = level.getChunk(sx, sz, ChunkStatus.FULL, false);
            if (chunk == null) continue;
            OccluderMesh mesh = build(chunk, sx, sy, sz, cx, cy, cz, r);
            OccluderMesh old = built.put(k, mesh);
            if (old != null) old.close();
            DIRTY.remove(k);
        }
        DIRTY.removeIf(k -> !built.containsKey(k));

        drawn.clear();
        for (int i = 0; i < wanted.size(); i++) {
            OccluderMesh m = built.get(wanted.getLong(i));
            if (m != null) drawn.add(m);
        }
        return drawn;
    }

    void clear() {
        for (OccluderMesh m : built.values()) if (m != null) m.close();
        built.clear();
        drawn.clear();
        level = null;
    }

    private OccluderMesh build(ChunkAccess chunk, int sx, int sy, int sz, float cx, float cy, float cz, float r) {
        LevelChunkSection[] sections = chunk.getSections();
        int si = sy - minSection();
        if (si < 0 || si >= sections.length) return null;
        LevelChunkSection section = sections[si];
        if (section == null || section.hasOnlyAir()) return null;
        int ox = sx << 4, oy = sy << 4, oz = sz << 4;
        float r2 = r * r;
        scratch.clear();
        for (int x = 0; x < 16; x++) {
            float dx = ox + x + 0.5f - cx;
            for (int z = 0; z < 16; z++) {
                float dz = oz + z + 0.5f - cz;
                float dxz = dx * dx + dz * dz;
                if (dxz > r2) continue;
                for (int y = 0; y < 16; y++) {
                    float dy = oy + y + 0.5f - cy;
                    if (dxz + dy * dy > r2) continue;
                    BlockState state = section.getBlockState(x, y, z);
                    if (state.isAir()) continue;
                    at.set(ox + x, oy + y, oz + z);
                    OccluderCollector.collect(level, state, at, scratch);
                }
            }
        }
        if (scratch.isEmpty()) return null;
        return OccluderMesh.build(scratch, ox, oy, oz, (x, y, z) -> {
            if (x >> 4 == sx && y >> 4 == sy && z >> 4 == sz) return false;
            float dx = x + 0.5f - cx, dy = y + 0.5f - cy, dz = z + 0.5f - cz;
            if (dx * dx + dy * dy + dz * dz > r2) return false;
            probe.set(x, y, z);
            return OccluderCollector.solid(level, level.getBlockState(probe), probe);
        });
    }

    private static float gap(float c, int s) {
        float lo = s << 4, hi = lo + 16f;
        float d = c < lo ? lo - c : c > hi ? c - hi : 0f;
        return d * d;
    }

    private static float distance(long k, float cx, float cy, float cz) {
        return gap(cx, sectionX(k)) + gap(cy, sectionY(k)) + gap(cz, sectionZ(k));
    }

    private static long key(int sx, int sy, int sz) {
        return ((long) sx & 0x3FFFFF) | (((long) sz & 0x3FFFFF) << 22) | (((long) sy & 0xFFFFF) << 44);
    }

    private static int sectionX(long k) { return (int) (k << 42 >> 42); }
    private static int sectionZ(long k) { return (int) (k << 20 >> 42); }
    private static int sectionY(long k) { return (int) (k >> 44); }

    private int minSection() {
        //? if >=1.21.2 {
        return level.getMinSectionY();
        //?} else {
        /*return level.getMinSection();
        *///?}
    }
}
