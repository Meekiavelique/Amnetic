package com.meekdev.amnetic.client.shadow.internal;

import com.meekdev.amnetic.client.shadow.ShadowShapes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import java.util.ArrayList;
import java.util.stream.IntStream;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OccluderCache {
    private static final long EMPTY = 0L;
    private static final float SNAP_PAD = 0.87f;
    private static final float MOVEMENT_PAD = 2.0f;
    private static final long FNV_OFFSET = 1469598103934665603L;
    private static final long FNV_PRIME = 1099511628211L;
    private static final long SECTION_MASK = 0x1FFFFF;

    private static final class Entry {
        long hash;
        List<OccluderEntry> list;
        List<OccluderEntry> baseList;
        List<OccluderEntry> tracked = List.of();
        int[] dynamicIndices = new int[0];
        long[] sectionKeys;
        float cx;
        float cy;
        float cz;
        float cr;
        int anchorX;
        int anchorY;
        int anchorZ;
        int hostX;
        int hostY;
        int hostZ;
        float range;
    }

    private static final Map<Long, Entry> byId = new HashMap<>();
    private static final Map<Long, Set<Long>> sectionToIds = new HashMap<>();
    private static Level cachedLevel;

    private OccluderCache() {}

    public static final class Collected {
        public final List<OccluderEntry> list;
        public final int anchorX;
        public final int anchorY;
        public final int anchorZ;

        Collected(List<OccluderEntry> list, int anchorX, int anchorY, int anchorZ) {
            this.list = list;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
        }
    }

    public static Collected getOrCompute(long id, Level level, float lx, float ly, float lz, float radius) {
        if (cachedLevel != level) {
            clear();
            cachedLevel = level;
        }
        float cx = Math.round(lx);
        float cy = Math.round(ly);
        float cz = Math.round(lz);
        float cr = (float) Math.ceil(radius) + SNAP_PAD + MOVEMENT_PAD;
        int hostX = (int) Math.floor(lx);
        int hostY = (int) Math.floor(ly);
        int hostZ = (int) Math.floor(lz);

        long h = hash(cx, cy, cz, cr);
        Entry e = byId.get(id);
        if (e != null && stillCovers(e, level, lx, ly, lz, radius, hostX, hostY, hostZ)) {
            refreshDynamic(e, level);
            return collected(e);
        }

        List<OccluderEntry> fresh = OccluderCollector.collectForLight(level, cx, cy, cz, cr, hostX, hostY, hostZ);
        if (e == null) {
            e = new Entry();
            byId.put(id, e);
        }
        e.list = fresh;
        e.baseList = fresh;
        e.tracked = List.of();
        e.dynamicIndices = IntStream.range(0, fresh.size())
                .filter(i -> ShadowShapes.provider(level.getBlockState(fresh.get(i).pos)) != null)
                .toArray();
        e.hash = h;
        e.hostX = hostX;
        e.hostY = hostY;
        e.hostZ = hostZ;
        e.range = radius;
        e.cx = cx;
        e.cy = cy;
        e.cz = cz;
        e.cr = cr;
        e.anchorX = Math.round(cx);
        e.anchorY = Math.round(cy);
        e.anchorZ = Math.round(cz);
        rebuildSectionIndex(id, e, cx, cy, cz, cr);
        refreshDynamic(e, level);
        return collected(e);
    }

    private static Collected collected(Entry e) {
        return new Collected(e.list, e.anchorX, e.anchorY, e.anchorZ);
    }

    private static boolean stillCovers(Entry e, Level level, float lx, float ly, float lz, float radius,
                                       int hostX, int hostY, int hostZ) {
        float dx = lx - e.cx;
        float dy = ly - e.cy;
        float dz = lz - e.cz;
        boolean sameHost = e.hostX == hostX && e.hostY == hostY && e.hostZ == hostZ
                || level.getBlockState(new BlockPos(e.hostX, e.hostY, e.hostZ)).getLightEmission() == 0
                && level.getBlockState(new BlockPos(hostX, hostY, hostZ)).getLightEmission() == 0;
        return e.hash != EMPTY && e.range == radius && e.list != null && sameHost
                && Math.sqrt(dx * dx + dy * dy + dz * dz) + radius + SNAP_PAD <= e.cr;
    }

    private static void refreshDynamic(Entry entry, Level level) {
        ArrayList<OccluderEntry> changed = null;
        for (int index : entry.dynamicIndices) {
            OccluderEntry previous = entry.baseList.get(index);
            if (!level.hasChunkAt(previous.pos)) continue;
            var state = level.getBlockState(previous.pos);
            var provider = ShadowShapes.provider(state);
            var next = provider == null ? Shapes.empty() : provider.shape(level, previous.pos, state);
            if (next == null) next = Shapes.empty();
            if (previous.shape == next) continue;
            if (previous.shape != null && !Shapes.joinIsNotEmpty(previous.shape, next, BooleanOp.NOT_SAME)) continue;
            if (changed == null) changed = new ArrayList<>(entry.baseList);
            changed.set(index, new OccluderEntry(previous.pos, next));
        }
        boolean updated = changed != null;
        if (changed != null) entry.baseList = changed;
        var tracked = ShadowShapes.nearby(level, entry.cx, entry.cy, entry.cz, entry.cr);
        if (!sameGeometry(entry.tracked, tracked)) {
            entry.tracked = tracked;
            updated = true;
        }
        if (!updated) return;
        if (entry.tracked.isEmpty()) {
            entry.list = entry.baseList;
        } else {
            var combined = new ArrayList<>(entry.baseList);
            combined.addAll(entry.tracked);
            entry.list = combined;
        }
    }

    private static boolean sameGeometry(List<OccluderEntry> a, List<OccluderEntry> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            var old = a.get(i);
            var next = b.get(i);
            if (!old.pos.equals(next.pos)) return false;
            if (old.shape == next.shape) continue;
            if (old.shape == null || next.shape == null) return false;
            if (Shapes.joinIsNotEmpty(old.shape, next.shape, BooleanOp.NOT_SAME)) return false;
        }
        return true;
    }

    public static void invalidateAt(BlockPos pos) {
        SunOccluders.blockChanged(pos);
        OccluderCollector.invalidateAt(pos);
        var level = Minecraft.getInstance().level;
        if (level != null && ShadowShapes.provider(level.getBlockState(pos)) != null) ShadowShapes.track(level, pos);
        long key = sectionKey(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        Set<Long> ids = sectionToIds.get(key);
        if (ids == null || ids.isEmpty()) return;
        float bx = pos.getX() + 0.5f;
        float by = pos.getY() + 0.5f;
        float bz = pos.getZ() + 0.5f;
        for (long id : ids) {
            Entry e = byId.get(id);
            if (e == null) continue;
            float dx = bx - e.cx;
            float dy = by - e.cy;
            float dz = bz - e.cz;
            if (dx * dx + dy * dy + dz * dz > e.cr * e.cr) continue;
            e.hash = EMPTY;
        }
    }

    public static void retainOnly(Set<Long> liveIds) {
        if (byId.isEmpty()) return;
        Iterator<Map.Entry<Long, Entry>> it = byId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Entry> me = it.next();
            if (!liveIds.contains(me.getKey())) {
                removeSectionKeys(me.getKey(), me.getValue().sectionKeys);
                it.remove();
            }
        }
    }

    public static void clear() {
        OccluderCollector.clear();
        cachedLevel = null;
        byId.clear();
        sectionToIds.clear();
    }

    private static long hash(float x, float y, float z, float r) {
        long h = FNV_OFFSET;
        h = (h ^ Float.floatToRawIntBits(x)) * FNV_PRIME;
        h = (h ^ Float.floatToRawIntBits(y)) * FNV_PRIME;
        h = (h ^ Float.floatToRawIntBits(z)) * FNV_PRIME;
        h = (h ^ Float.floatToRawIntBits(r)) * FNV_PRIME;
        return h == EMPTY ? 1L : h;
    }

    private static long sectionKey(int sx, int sy, int sz) {
        return (sx & SECTION_MASK) | ((sy & SECTION_MASK) << 21) | ((sz & SECTION_MASK) << 42);
    }

    private static long[] computeSectionsForSphere(float lx, float ly, float lz, float r) {
        int minSx = ((int) Math.floor(lx - r)) >> 4;
        int minSy = ((int) Math.floor(ly - r)) >> 4;
        int minSz = ((int) Math.floor(lz - r)) >> 4;
        int maxSx = ((int) Math.floor(lx + r)) >> 4;
        int maxSy = ((int) Math.floor(ly + r)) >> 4;
        int maxSz = ((int) Math.floor(lz + r)) >> 4;
        long[] keys = new long[(maxSx - minSx + 1) * (maxSy - minSy + 1) * (maxSz - minSz + 1)];
        int k = 0;
        for (int sx = minSx; sx <= maxSx; sx++) {
            for (int sy = minSy; sy <= maxSy; sy++) {
                for (int sz = minSz; sz <= maxSz; sz++) {
                    keys[k] = sectionKey(sx, sy, sz);
                    k++;
                }
            }
        }
        return keys;
    }

    private static void rebuildSectionIndex(long id, Entry e, float lx, float ly, float lz, float r) {
        if (e.sectionKeys != null) removeSectionKeys(id, e.sectionKeys);
        long[] newKeys = computeSectionsForSphere(lx, ly, lz, r);
        for (long key : newKeys) {
            sectionToIds.computeIfAbsent(key, k -> new HashSet<>(4)).add(id);
        }
        e.sectionKeys = newKeys;
    }

    private static void removeSectionKeys(long id, long[] keys) {
        if (keys == null) return;
        for (long key : keys) {
            Set<Long> s = sectionToIds.get(key);
            if (s != null) {
                s.remove(id);
                if (s.isEmpty()) sectionToIds.remove(key);
            }
        }
    }
}
