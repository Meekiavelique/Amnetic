package com.meekdev.amnetic.client.shadow.internal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class OccluderEntry {

    public final BlockPos pos;
    public final VoxelShape shape;
    public final boolean full;
    public final float[] cutoutVerts;
    public final float[] translucentVerts;

    public OccluderEntry(BlockPos pos, VoxelShape shape) {
        this(pos, shape, !shape.isEmpty() && shape.equals(Shapes.block()));
    }

    public OccluderEntry(BlockPos pos, VoxelShape shape, boolean full) {
        this.pos = pos;
        this.shape = shape;
        this.full = full;
        this.cutoutVerts = null;
        this.translucentVerts = null;
    }

    public OccluderEntry(BlockPos pos, float[] cutoutVerts, float[] translucentVerts) {
        this.pos = pos;
        this.shape = null;
        this.full = false;
        this.cutoutVerts = cutoutVerts;
        this.translucentVerts = translucentVerts;
    }

    public boolean isTextured() { return cutoutVerts != null || translucentVerts != null; }
}
