package com.meekdev.amnetic.client.shadow.internal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

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

    private static final class Entry {
        long hash;
        List<OccluderEntry> list;
        List<OccluderEntry> baseList;
        List<OccluderEntry> tracked = List.of();
        int[] dynamicIndices = new int[0];
        long[] sectionKeys;
        float cx, cy, cz, cr;
        int anchorX, anchorY, anchorZ; // integer collection center, the mesh's local origin
        int hostX, hostY, hostZ;
        float range;
    }

    private static final Map<Long, Entry> byId = new HashMap<>();
    private static final Map<Long, Set<Long>> sectionToIds = new HashMap<>();
    private static Level cachedLevel;

    private OccluderCache() {}

    public static final class Collected {
        public final List<OccluderEntry> list;
        public final int anchorX, anchorY, anchorZ;
        Collected(List<OccluderEntry> list, int ax, int ay, int az) {
            this.list = list; this.anchorX = ax; this.anchorY = ay; this.anchorZ = az;
        }
    }

    public static Collected getOrCompute(long id, Level level, float lx, float ly, float lz, float radius) {
        if (cachedLevel != level) { clear(); cachedLevel = level; }
        float cx = Math.round(lx), cy = Math.round(ly), cz = Math.round(lz);
        float cr = (float) Math.ceil(radius) + SNAP_PAD + MOVEMENT_PAD;
        int hostX = (int) Math.floor(lx), hostY = (int) Math.floor(ly), hostZ = (int) Math.floor(lz);

        long h = hash(cx, cy, cz, cr);
        Entry e = byId.get(id);
        float dx=e==null?0:lx-e.cx, dy=e==null?0:ly-e.cy, dz=e==null?0:lz-e.cz;
        boolean sameHost=e!=null && (e.hostX==hostX && e.hostY==hostY && e.hostZ==hostZ
                || level.getBlockState(new BlockPos(e.hostX,e.hostY,e.hostZ)).getLightEmission()==0
                && level.getBlockState(new BlockPos(hostX,hostY,hostZ)).getLightEmission()==0);
        if (e != null && e.hash != EMPTY && e.range==radius && e.list != null && sameHost
                && Math.sqrt(dx*dx+dy*dy+dz*dz)+radius+SNAP_PAD<=e.cr) {
            refreshDynamic(e, level);
            return new Collected(e.list, e.anchorX, e.anchorY, e.anchorZ);
        }

        List<OccluderEntry> fresh = OccluderCollector.collectForLight(level, cx, cy, cz, cr, hostX, hostY, hostZ);
        if (e == null) { e = new Entry(); byId.put(id, e); }
        e.list = fresh; e.baseList = fresh; e.tracked = List.of();
        e.dynamicIndices = java.util.stream.IntStream.range(0, fresh.size())
                .filter(i -> com.meekdev.amnetic.client.shadow.ShadowShapes.provider(level.getBlockState(fresh.get(i).pos)) != null)
                .toArray();
        e.hash = h;
        e.hostX=hostX; e.hostY=hostY; e.hostZ=hostZ; e.range=radius;
        e.cx = cx; e.cy = cy; e.cz = cz; e.cr = cr;
        e.anchorX = Math.round(cx); e.anchorY = Math.round(cy); e.anchorZ = Math.round(cz);
        rebuildSectionIndex(id, e, cx, cy, cz, cr);
        refreshDynamic(e,level);
        return new Collected(e.list, e.anchorX, e.anchorY, e.anchorZ);
    }

    private static void refreshDynamic(Entry entry, Level level) {
        java.util.ArrayList<OccluderEntry> changed = null;
        for (int index : entry.dynamicIndices) {
            OccluderEntry previous = entry.baseList.get(index);
            if (!level.hasChunkAt(previous.pos)) continue;
            var state = level.getBlockState(previous.pos);
            var provider = com.meekdev.amnetic.client.shadow.ShadowShapes.provider(state);
            var next = provider == null ? net.minecraft.world.phys.shapes.Shapes.empty() : provider.shape(level, previous.pos, state);
            if (next == null) next = net.minecraft.world.phys.shapes.Shapes.empty();
            if (previous.shape==next || previous.shape != null && !net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(previous.shape, next,
                    net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME)) continue;
            if (changed == null) changed = new java.util.ArrayList<>(entry.baseList);
            changed.set(index, new OccluderEntry(previous.pos, next));
        }
        boolean updated=changed!=null;
        if (changed != null) entry.baseList = changed;
        var tracked=com.meekdev.amnetic.client.shadow.ShadowShapes.nearby(level,entry.cx,entry.cy,entry.cz,entry.cr);
        if(!sameGeometry(entry.tracked,tracked)){entry.tracked=tracked;updated=true;}
        if(updated) {
            if(entry.tracked.isEmpty())entry.list=entry.baseList;
            else {var combined=new java.util.ArrayList<>(entry.baseList);combined.addAll(entry.tracked);entry.list=combined;}
        }
    }

    private static boolean sameGeometry(List<OccluderEntry> a,List<OccluderEntry> b) {
        if(a.size()!=b.size())return false;
        for(int i=0;i<a.size();i++) {
            var old=a.get(i);var next=b.get(i);
            if(!old.pos.equals(next.pos))return false;
            if(old.shape==next.shape)continue;
            if(old.shape==null || next.shape==null || net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(old.shape,next.shape,net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME))return false;
        }
        return true;
    }

    public static void invalidateAt(BlockPos pos) {
        SunOccluders.blockChanged(pos);
        OccluderCollector.invalidateAt(pos);
        var level=net.minecraft.client.Minecraft.getInstance().level;
        if(level!=null && com.meekdev.amnetic.client.shadow.ShadowShapes.provider(level.getBlockState(pos))!=null)
            com.meekdev.amnetic.client.shadow.ShadowShapes.track(level,pos);
        long key = sectionKey(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        Set<Long> ids = sectionToIds.get(key);
        if (ids == null || ids.isEmpty()) return;
        float bx = pos.getX() + 0.5f, by = pos.getY() + 0.5f, bz = pos.getZ() + 0.5f;
        for (long id : ids) {
            Entry e = byId.get(id);
            if (e == null) continue;
            float dx = bx - e.cx, dy = by - e.cy, dz = bz - e.cz;
            if (dx * dx + dy * dy + dz * dz > e.cr * e.cr) continue; // far corner of a touched section
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
        long h = 1469598103934665603L;
        h = (h ^ Float.floatToRawIntBits(x)) * 1099511628211L;
        h = (h ^ Float.floatToRawIntBits(y)) * 1099511628211L;
        h = (h ^ Float.floatToRawIntBits(z)) * 1099511628211L;
        h = (h ^ Float.floatToRawIntBits(r)) * 1099511628211L;
        return h == EMPTY ? 1L : h;
    }

    private static long sectionKey(int sx, int sy, int sz) {
        return ((long) (sx & 0x1FFFFF)) | (((long) (sy & 0x1FFFFF)) << 21) | (((long) (sz & 0x1FFFFF)) << 42);
    }

    private static long[] computeSectionsForSphere(float lx, float ly, float lz, float r) {
        int minSx = ((int) Math.floor(lx - r)) >> 4, minSy = ((int) Math.floor(ly - r)) >> 4, minSz = ((int) Math.floor(lz - r)) >> 4;
        int maxSx = ((int) Math.floor(lx + r)) >> 4, maxSy = ((int) Math.floor(ly + r)) >> 4, maxSz = ((int) Math.floor(lz + r)) >> 4;
        long[] keys = new long[(maxSx - minSx + 1) * (maxSy - minSy + 1) * (maxSz - minSz + 1)];
        int k = 0;
        for (int sx = minSx; sx <= maxSx; sx++)
            for (int sy = minSy; sy <= maxSy; sy++)
                for (int sz = minSz; sz <= maxSz; sz++)
                    keys[k++] = sectionKey(sx, sy, sz);
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
