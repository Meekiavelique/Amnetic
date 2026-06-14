package com.meekdev.amnetic.client.geometry;

import com.meekdev.amnetic.client.geometry.internal.MeshTapRegistry;
import net.minecraft.world.entity.Entity;

public final class EntityMeshTap {

    private EntityMeshTap() {}

    public static PosedMesh tap(Entity entity) {
        PosedMesh mesh = new PosedMesh(entity);
        MeshTapRegistry.INSTANCE.put(entity, mesh);
        return mesh;
    }

    public static void clear() {
        MeshTapRegistry.INSTANCE.clear();
    }
}
