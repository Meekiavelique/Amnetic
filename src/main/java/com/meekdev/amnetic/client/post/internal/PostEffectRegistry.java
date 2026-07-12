package com.meekdev.amnetic.client.post.internal;

import com.meekdev.amnetic.client.post.RenderPhase;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.Identifier;

public final class PostEffectRegistry {

    public static final PostEffectRegistry INSTANCE = new PostEffectRegistry();

    private final List<PostEffectEntry> entries = new CopyOnWriteArrayList<>();

    private PostEffectRegistry() {}

    public PostEffectEntry register(Identifier id) {
        PostEffectEntry entry = new PostEffectEntry(id);
        entries.add(entry);
        resort();
        return entry;
    }

    // priority can change after registration (configurator, handle.setPriority), callers resort
    public void resort() {
        entries.sort(Comparator.comparingInt(PostEffectEntry::getPriority).reversed());
    }

    public void unregister(PostEffectEntry entry) {
        entry.close();
        entries.remove(entry);
    }

    public void applyAll(RenderPhase phase, float deltaTick, GraphicsResourceAllocator allocator) {
        for (PostEffectEntry entry : entries) {
            entry.apply(phase, deltaTick, allocator);
        }
    }

    public boolean hasEnabledEffectInPhase(RenderPhase phase) {
        for (PostEffectEntry entry : entries) {
            if (entry.getPhase() == phase && entry.isEnabled()) return true;
        }
        return false;
    }

    public void captureWorldDepthSnapshot(RenderTarget framebuffer) {
        WorldDepthSnapshot.capture(framebuffer);
    }

    public void capturePostRenderDepthSnapshot(RenderTarget framebuffer) {
        PostRenderDepthSnapshot.capture(framebuffer);
    }

    public void restorePostRenderDepthSnapshotInto(RenderTarget framebuffer) {
        if (!PostRenderDepthSnapshot.restoreInto(framebuffer)) {
            WorldDepthSnapshot.restoreInto(framebuffer);
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
