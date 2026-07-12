package com.meekdev.amnetic.client.render;

import java.util.ArrayList;
import java.util.List;

public final class AfterWorldRender {

    private static final List<Runnable> CALLBACKS = new ArrayList<>();

    private AfterWorldRender() {}

    public static void register(Runnable callback) {
        CALLBACKS.add(callback);
    }

    public static void fire() {
        for (int i = 0; i < CALLBACKS.size(); i++) {
            try {
                CALLBACKS.get(i).run();
            } catch (Throwable e) {
                // never crash the frame
            }
        }
    }
}
