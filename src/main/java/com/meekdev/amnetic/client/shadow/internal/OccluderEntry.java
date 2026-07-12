package com.meekdev.amnetic.client.shadow.internal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class OccluderEntry {

    public final BlockPos pos;
    public final VoxelShape shape;
    public final float[] cutoutVerts;
    public final float[] translucentVerts;

    public OccluderEntry(BlockPos pos, VoxelShape shape) {
        this.pos = pos;
        this.shape = shape;
        this.cutoutVerts = null;
        this.translucentVerts = null;
    }

    public OccluderEntry(BlockPos pos, float[] cutoutVerts, float[] translucentVerts) {
        this.pos = pos;
        this.shape = null;
        this.cutoutVerts = cutoutVerts;
        this.translucentVerts = translucentVerts;
    }

    public boolean isTextured() { return cutoutVerts != null || translucentVerts != null; }
}
