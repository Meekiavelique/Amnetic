package com.meekdev.amnetic.client.framebuffer.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FramebufferRegistry {

    public static final FramebufferRegistry INSTANCE = new FramebufferRegistry();

    private final CopyOnWriteArrayList<GlFramebuffer> live = new CopyOnWriteArrayList<>();
    //? if >=26.2 {
    private final CopyOnWriteArrayList<NativeFramebuffer> nativeLive = new CopyOnWriteArrayList<>();

    public void register(NativeFramebuffer fb) { nativeLive.addIfAbsent(fb); }
    public void deregister(NativeFramebuffer fb) { nativeLive.remove(fb); }
    public List<NativeFramebuffer> registeredNative() { return new ArrayList<>(nativeLive); }
    //?}

    private FramebufferRegistry() {}

    public void register(GlFramebuffer fb) {
        live.addIfAbsent(fb);
    }

    public void deregister(GlFramebuffer fb) {
        live.remove(fb);
    }

    public List<GlFramebuffer> registered() {
        return new ArrayList<>(live);
    }

    public void closeAll() {
        //? if >=26.2 {
        for (var fb : nativeLive) fb.close();
        nativeLive.clear();
        //?}
        for (GlFramebuffer fb : live) {
            fb.dispose();
        }
        live.clear();
    }
}
