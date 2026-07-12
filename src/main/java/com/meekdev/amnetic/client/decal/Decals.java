package com.meekdev.amnetic.client.decal;

import com.meekdev.amnetic.client.decal.internal.DecalRenderer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;


public final class Decals {

    static final CopyOnWriteArrayList<Decal> ACTIVE = new CopyOnWriteArrayList<>();

    private Decals() {}

    public static Decal box(Identifier texture, Vec3 center, Vector3f normal,
                            float width, float height, float depth) {
        Decal d = new Decal(texture, center, normal, width, height, depth);
        ACTIVE.add(d);
        return d;
    }

    public static List<Decal> active() { return ACTIVE; }

    public static boolean hasRelightable() {
        for (Decal d : ACTIVE) if (!d.isRemoved() && d.writesGBuffer()) return true;
        return false;
    }

    public static void clear() { ACTIVE.clear(); }

    public static void render() { DecalRenderer.INSTANCE.render(ACTIVE); }

    public static void dispose() { DecalRenderer.INSTANCE.dispose(); }
}
