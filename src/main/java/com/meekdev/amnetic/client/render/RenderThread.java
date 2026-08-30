package com.meekdev.amnetic.client.render;

import com.meekdev.amnetic.client.model.internal.GlUploadQueue;

public final class RenderThread {

    private RenderThread() {
    }

    public static void submit(Runnable task) {
        GlUploadQueue.submit(task);
    }

    public static void background(Runnable task) {
        GlUploadQueue.decode(task);
    }

    public static boolean idle() {
        return GlUploadQueue.idle();
    }
}
