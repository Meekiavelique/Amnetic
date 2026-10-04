package com.meekdev.amnetic.client.compat;

import com.meekdev.amnetic.platform.Platform;

public final class Sodium {

    private static final boolean PRESENT =
            Platform.isModLoaded("sodium") || Platform.isModLoaded("embeddium");

    private Sodium() {
    }

    public static boolean present() {
        return PRESENT;
    }
}
