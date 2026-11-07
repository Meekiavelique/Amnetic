package com.meekdev.amnetic.client.decal;

import com.meekdev.amnetic.client.decal.internal.DecalRenderer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class Decals {
    static final CopyOnWriteArrayList<Decal> ACTIVE = new CopyOnWriteArrayList<>();
    static final CopyOnWriteArrayList<FilmDecal> FILMS = new CopyOnWriteArrayList<>();
    static final float[] ENVIRONMENT = {0.3f, 0.9f, 0.3f, 1.0f, 0.95f, 0.85f, 0.55f, 0.65f, 0.85f, 1.0f, 0.75f, 0.45f};

    private Decals() {}

    public static Decal box(Identifier texture, Vec3 center, Vector3f normal,
                            float width, float height, float depth) {
        Decal d = new Decal(texture, center, normal, width, height, depth);
        ACTIVE.add(d);
        return d;
    }

    public static List<Decal> active() { return ACTIVE; }

    public static FilmDecal film(double x, double minY, double z, float sizeX, float sizeY, float sizeZ, int resX, int resZ, FilmLook look) {
        FilmDecal f = new FilmDecal(x, minY, z, sizeX, sizeY, sizeZ, resX, resZ, look);
        FILMS.add(f);
        return f;
    }

    public static List<FilmDecal> films() { return FILMS; }

    public static void filmEnvironment(float sunX, float sunY, float sunZ, float sunR, float sunG, float sunB,
                                       float skyR, float skyG, float skyB, float blockR, float blockG, float blockB) {
        float l = (float) Math.sqrt(sunX * sunX + sunY * sunY + sunZ * sunZ);
        if (l < 1e-6f) {
            sunX = 0f;
            sunY = 1f;
            sunZ = 0f;
            l = 1f;
        }
        float[] e = {sunX / l, sunY / l, sunZ / l, sunR, sunG, sunB, skyR, skyG, skyB, blockR, blockG, blockB};
        System.arraycopy(e, 0, ENVIRONMENT, 0, e.length);
    }

    public static float[] filmEnvironment() { return ENVIRONMENT; }

    public static void clearFilms() {
        for (FilmDecal f : FILMS) f.remove();
    }

    public static void touch() {
        FilmPass.INSTANCE.hashCode();
        DecalRenderer.INSTANCE.hashCode();
    }

    public static void renderFilms() { FilmPass.INSTANCE.render(FILMS); }

    public static boolean hasRelightable() {
        for (Decal d : ACTIVE) {
            if (!d.isRemoved() && d.writesGBuffer()) return true;
        }
        return false;
    }

    public static void clear() { ACTIVE.clear(); }

    public static void render() { DecalRenderer.INSTANCE.render(ACTIVE); }

    public static void dispose() {
        DecalRenderer.INSTANCE.dispose();
        FilmPass.INSTANCE.dispose();
    }
}
