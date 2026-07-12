package com.meekdev.amnetic.client.ui.scene;

import com.meekdev.amnetic.client.decal.Decal;
import com.meekdev.amnetic.client.decal.Decals;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.internal.LightRegistry;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * enumerates the live scene objects as {@link Selectable} adapters, rebuilt each frame
 * stable display names are cached by object identity
 */
public final class SelectableRegistry {

    private static final Map<Object, String> NAMES = new IdentityHashMap<>();
    private static int counter;

    private SelectableRegistry() {}

    public static List<Selectable> all() {
        List<Selectable> out = new ArrayList<>();
        for (Light l : LightRegistry.INSTANCE.all()) {
            out.add(new LightSelectable(l, name(l, "light")));
        }
        for (Decal d : Decals.active()) {
            if (!d.isRemoved()) out.add(new DecalSelectable(d, name(d, "decal")));
        }
        return out;
    }

    private static String name(Object o, String prefix) {
        String n = NAMES.get(o);
        if (n == null) { n = prefix + "_" + (counter++); NAMES.put(o, n); }
        return n;
    }
}
