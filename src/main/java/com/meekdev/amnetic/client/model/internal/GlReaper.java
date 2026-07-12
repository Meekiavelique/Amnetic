package com.meekdev.amnetic.client.model.internal;

import java.util.concurrent.ConcurrentLinkedQueue;

import com.mojang.blaze3d.systems.RenderSystem;

public final class GlReaper {

    private static final ConcurrentLinkedQueue<Runnable> PENDING = new ConcurrentLinkedQueue<>();

    private GlReaper() {
    }

    public static void submit(Runnable deletion) {
        if (RenderSystem.isOnRenderThread()) {
            deletion.run();
        } else {
            PENDING.add(deletion);
        }
    }

    public static void drain() {
        Runnable task;
        while ((task = PENDING.poll()) != null) {
            task.run();
        }
    }
}
