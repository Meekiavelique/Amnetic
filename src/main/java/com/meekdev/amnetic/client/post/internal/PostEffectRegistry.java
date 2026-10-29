package com.meekdev.amnetic.client.post.internal;

import com.meekdev.amnetic.client.post.RenderPhase;
import com.mojang.blaze3d.pipeline.RenderTarget;
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

    public void applyAll(RenderPhase phase, float deltaTick) {
        boolean profiling = com.meekdev.amnetic.client.pipeline.PassProfiler.INSTANCE.active();
        for (PostEffectEntry entry : entries) {
            if (!profiling || entry.getPhase() != phase || !entry.isEnabled()) {
                entry.apply(phase, deltaTick);
                continue;
            }
            String label = "post " + entry.getId().getPath();
            com.meekdev.amnetic.client.pipeline.PassProfiler.INSTANCE.begin(label);
            entry.apply(phase, deltaTick);
            com.meekdev.amnetic.client.pipeline.PassProfiler.INSTANCE.end(label);
        }
    }

    // starts and takes in the programs of every effect, true while some are left
    public boolean warm() {
        boolean worked = false;
        for (PostEffectEntry entry : new java.util.ArrayList<>(entries)) {
            if (entry.warm()) worked = true;
        }
        return worked;
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
