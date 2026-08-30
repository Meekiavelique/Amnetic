package com.meekdev.amnetic.client.compat;

import net.fabricmc.loader.api.FabricLoader;

public final class Sodium {

    private static final boolean PRESENT =
            FabricLoader.getInstance().isModLoaded("sodium")
                    || FabricLoader.getInstance().isModLoaded("embeddium");

    private Sodium() {
    }

    public static boolean present() {
        return PRESENT;
    }
}
