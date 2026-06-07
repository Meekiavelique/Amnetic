package com.meekdev.amnetic.client.particle;

import java.util.Random;
import java.util.function.Consumer;
import org.joml.Vector3f;

public final class Particles {

    private static final ThreadLocal<ParticleOverrides> OVERRIDES = ThreadLocal.withInitial(ParticleOverrides::new);
    private static final ThreadLocal<Random> RNG = ThreadLocal.withInitial(Random::new);
    private static final ThreadLocal<Vector3f> TMP_OFF = ThreadLocal.withInitial(Vector3f::new);
    private static final ThreadLocal<Vector3f> TMP_DIR = ThreadLocal.withInitial(Vector3f::new);

    private Particles() {}

    public static void init() {}

    public static ParticleMaterial.Builder material() {
        return new ParticleMaterial.Builder();
    }

    public static void spawn(ParticleMaterial material,
                             double x, double y, double z,
                             double vx, double vy, double vz) {
        spawn(material, x, y, z, vx, vy, vz, null);
    }

    public static void spawn(ParticleMaterial material,
                             double x, double y, double z,
                             double vx, double vy, double vz,
                             Consumer<ParticleOverrides> overrides) {
        ParticleOverrides o = OVERRIDES.get();
        o.seedFrom(material);
        if (overrides != null) overrides.accept(o);

        Random rng = RNG.get();
        ParticleSimulation.SpawnRequest req = ParticleSimulation.INSTANCE.borrowRequest();
        req.material = material;
        req.x = x; req.y = y; req.z = z;
        req.vx = vx; req.vy = vy; req.vz = vz;
        req.life = o.life;
        req.size0 = o.size0; req.size1 = o.size1;
        req.r0 = o.r0; req.g0 = o.g0; req.b0 = o.b0;
        req.r1 = o.r1; req.g1 = o.g1; req.b1 = o.b1;
        req.a0 = o.a0; req.a1 = o.a1;
        req.gravity = o.gravity; req.drag = o.drag;
        req.rot = o.rot; req.rotSpeed = o.rotSpeed;
        req.easing = o.sizeEasing;
        req.seedX = rng.nextFloat() * 4f;
        req.seedY = rng.nextFloat() * 4f;
        ParticleSimulation.INSTANCE.submit(req);
    }

    @FunctionalInterface
    public interface BurstCustomizer {
        void customize(ParticleOverrides o, Random rng, int index);
    }

    public static void burst(ParticleMaterial material, double x, double y, double z,
                             int count, SpawnShape shape, float speedMin, float speedMax) {
        burst(material, x, y, z, count, shape, speedMin, speedMax, null);
    }

    public static void burst(ParticleMaterial material, double x, double y, double z,
                             int count, SpawnShape shape, float speedMin, float speedMax,
                             BurstCustomizer customizer) {
        Random rng = RNG.get();
        Vector3f off = TMP_OFF.get();
        Vector3f dir = TMP_DIR.get();
        for (int i = 0; i < count; i++) {
            shape.sample(rng, off, dir);
            float speed = speedMin + rng.nextFloat() * (speedMax - speedMin);
            final int index = i;
            spawn(material,
                    x + off.x, y + off.y, z + off.z,
                    dir.x * speed, dir.y * speed, dir.z * speed,
                    customizer == null ? null : o -> customizer.customize(o, rng, index));
        }
    }

    public static int liveCount() {
        return ParticleSimulation.INSTANCE.liveCount();
    }
}