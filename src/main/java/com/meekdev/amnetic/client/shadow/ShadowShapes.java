package com.meekdev.amnetic.client.shadow;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Render-thread registrations for block-entity geometry which changes without a block-state update.
 * Shapes are in block-local coordinates and may extend outside the host cell. */
public final class ShadowShapes {
    @FunctionalInterface public interface Provider { VoxelShape shape(Level level, BlockPos pos, BlockState state); }
    private static final Map<Block,Provider> PROVIDERS = new IdentityHashMap<>();
    private static final Map<Level,java.util.Set<BlockPos>> TRACKED=new java.util.WeakHashMap<>();
    private ShadowShapes() {}
    public static void track(Level level,BlockPos pos) { TRACKED.computeIfAbsent(level,key->new java.util.HashSet<>()).add(pos.immutable()); }
    public static boolean isTracked(Level level,BlockPos pos) { return TRACKED.getOrDefault(level,java.util.Set.of()).contains(pos); }
    public static java.util.List<com.meekdev.amnetic.client.shadow.internal.OccluderEntry> nearby(Level level,float x,float y,float z,float radius) {
        var positions=TRACKED.get(level);if(positions==null || positions.isEmpty())return java.util.List.of();
        var out=new java.util.ArrayList<com.meekdev.amnetic.client.shadow.internal.OccluderEntry>();
        var area=new net.minecraft.world.phys.AABB(x-radius,y-radius,z-radius,x+radius,y+radius,z+radius);
        var iterator=positions.iterator();
        while(iterator.hasNext()) {
            var pos=iterator.next();
            if(!level.hasChunkAt(pos)){iterator.remove();continue;}
            var state=level.getBlockState(pos);var provider=provider(state);
            if(provider==null){iterator.remove();continue;}
            var shape=provider.shape(level,pos,state);
            if(shape!=null && !shape.isEmpty() && shape.bounds().move(pos).intersects(area))
                out.add(new com.meekdev.amnetic.client.shadow.internal.OccluderEntry(pos,shape));
        }
        return out;
    }
    public static void register(Block block, Provider provider) { PROVIDERS.put(block, provider); }
    public static void unregister(Block block) { PROVIDERS.remove(block); }
    public static Provider provider(BlockState state) { return PROVIDERS.get(state.getBlock()); }
}
