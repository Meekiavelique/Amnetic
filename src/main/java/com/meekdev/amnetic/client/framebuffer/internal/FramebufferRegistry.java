package com.meekdev.amnetic.client.framebuffer.internal;

import java.util.concurrent.CopyOnWriteArrayList;

public final class FramebufferRegistry {

    public static final FramebufferRegistry INSTANCE = new FramebufferRegistry();

    private final CopyOnWriteArrayList<GlFramebuffer> live = new CopyOnWriteArrayList<>();

    private FramebufferRegistry() {}

    public void register(GlFramebuffer fb) {
        live.addIfAbsent(fb);
    }

    public void deregister(GlFramebuffer fb) {
        live.remove(fb);
    }

    public void closeAll() {
        for (GlFramebuffer fb : live) {
            fb.dispose();
        }
        live.clear();
    }
}
