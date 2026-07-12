package com.meekdev.amnetic.client.shadow.internal;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OccluderMeshCache {

    private static final class Slot {
        OccluderMesh mesh;
        List<OccluderEntry> builtFrom; // identity of the list the mesh was built from
    }

    private final Map<Long, Slot> byId = new HashMap<>();

    public OccluderMesh get(long id, OccluderCache.Collected collected) {
        Slot slot = byId.get(id);
        if (slot != null && slot.builtFrom == collected.list) return slot.mesh;

        if (slot == null) { slot = new Slot(); byId.put(id, slot); }
        if (slot.mesh != null) slot.mesh.close();
        slot.mesh = OccluderMesh.build(collected.list, collected.anchorX, collected.anchorY, collected.anchorZ);
        slot.builtFrom = collected.list;
        return slot.mesh;
    }

    public void retainOnly(Set<Long> liveIds) {
        Iterator<Map.Entry<Long, Slot>> it = byId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Slot> me = it.next();
            if (!liveIds.contains(me.getKey())) {
                if (me.getValue().mesh != null) me.getValue().mesh.close();
                it.remove();
            }
        }
    }

    public void dispose() {
        for (Slot s : byId.values()) if (s.mesh != null) s.mesh.close();
        byId.clear();
    }
}
