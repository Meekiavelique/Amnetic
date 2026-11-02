package com.meekdev.amnetic.client.shadow;

import com.meekdev.amnetic.client.shadow.internal.OccluderEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class ShadowShapes {
    @FunctionalInterface
    public interface Provider {
        VoxelShape shape(Level level, BlockPos pos, BlockState state);
    }

    private static final Map<Block, Provider> PROVIDERS = new IdentityHashMap<>();
    private static final Map<Level, Set<BlockPos>> TRACKED = new WeakHashMap<>();

    private ShadowShapes() {}

    public static void track(Level level, BlockPos pos) {
        TRACKED.computeIfAbsent(level, key -> new HashSet<>()).add(pos.immutable());
    }

    public static boolean isTracked(Level level, BlockPos pos) {
        return TRACKED.getOrDefault(level, Set.of()).contains(pos);
    }

    public static List<OccluderEntry> nearby(Level level, float x, float y, float z, float radius) {
        var positions = TRACKED.get(level);
        if (positions == null || positions.isEmpty()) return List.of();
        var out = new ArrayList<OccluderEntry>();
        var area = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        var iterator = positions.iterator();
        while (iterator.hasNext()) {
            var pos = iterator.next();
            if (!level.hasChunkAt(pos)) {
                iterator.remove();
                continue;
            }
            var state = level.getBlockState(pos);
            var provider = provider(state);
            if (provider == null) {
                iterator.remove();
                continue;
            }
            var shape = provider.shape(level, pos, state);
            if (shape != null && !shape.isEmpty() && shape.bounds().move(pos).intersects(area)) {
                out.add(new OccluderEntry(pos, shape));
            }
        }
        return out;
    }

    public static void register(Block block, Provider provider) {
        PROVIDERS.put(block, provider);
    }

    public static void unregister(Block block) {
        PROVIDERS.remove(block);
    }

    public static Provider provider(BlockState state) {
        return PROVIDERS.get(state.getBlock());
    }
}
