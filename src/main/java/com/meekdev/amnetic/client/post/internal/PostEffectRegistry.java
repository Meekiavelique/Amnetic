package com.meekdev.amnetic.client.post.internal;

import com.meekdev.amnetic.client.post.RenderPhase;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PostEffectRegistry {

    public static final PostEffectRegistry INSTANCE = new PostEffectRegistry();

    private final List<PostEffectEntry> entries = new CopyOnWriteArrayList<>();

    private PostEffectRegistry() {}

    public PostEffectEntry register(Identifier id) {
        PostEffectEntry entry = new PostEffectEntry(id);
        entries.add(entry);
        entries.sort(Comparator.comparingInt(PostEffectEntry::getPriority).reversed());
        return entry;
    }

    public void unregister(PostEffectEntry entry) {
        entry.close();
        entries.remove(entry);
    }

    public void applyAll(RenderPhase phase, float deltaTick, ObjectAllocator allocator) {
        for (PostEffectEntry entry : entries) {
            entry.apply(phase, deltaTick, allocator);
        }
    }

    public void invalidatePipelineCaches() {
        for (PostEffectEntry entry : entries) {
            entry.invalidatePipelineCache();
        }
    }

    public void closeAll() {
        for (PostEffectEntry entry : entries) {
            entry.close();
        }
    }
}
