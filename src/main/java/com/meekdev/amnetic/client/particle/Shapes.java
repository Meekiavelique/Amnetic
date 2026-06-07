package com.meekdev.amnetic.client.particle;

import java.util.random.RandomGenerator;
import org.joml.Vector3f;

public final class Shapes {

    private Shapes() {}

    public static SpawnShape point() {
        return (rng, off, dir) -> { off.set(0); randomUnit(rng, dir); };
    }

    public static SpawnShape sphereSurface() {
        return point();
    }

    public static SpawnShape sphereVolume(float radius) {
        return (rng, off, dir) -> {
            randomUnit(rng, dir);
            float r = radius * (float) Math.cbrt(rng.nextDouble());
            off.set(dir).mul(r);
        };
    }

    public static SpawnShape cone(Vector3f axis, float halfAngleDeg) {
        Vector3f a = new Vector3f(axis).normalize();
        float cosMax = (float) Math.cos(Math.toRadians(halfAngleDeg));
        return (rng, off, dir) -> {
            off.set(0);
            double cosTheta = 1 - rng.nextDouble() * (1 - cosMax);
            double sinTheta = Math.sqrt(Math.max(0, 1 - cosTheta * cosTheta));
            double phi = rng.nextDouble() * Math.PI * 2;
            Vector3f local = new Vector3f(
                    (float) (Math.cos(phi) * sinTheta),
                    (float) (Math.sin(phi) * sinTheta),
                    (float) cosTheta);
            orientToAxis(a, local, dir);
        };
    }

    public static SpawnShape box(float halfX, float halfY, float halfZ) {
        return (rng, off, dir) -> {
            off.set(
                    (rng.nextFloat() * 2 - 1) * halfX,
                    (rng.nextFloat() * 2 - 1) * halfY,
                    (rng.nextFloat() * 2 - 1) * halfZ);
            dir.set(0, 1, 0);
        };
    }

    public static SpawnShape disc(Vector3f axis, float radius) {
        Vector3f a = new Vector3f(axis).normalize();
        return (rng, off, dir) -> {
            double ang = rng.nextDouble() * Math.PI * 2;
            float r = radius * (float) Math.sqrt(rng.nextDouble());
            Vector3f local = new Vector3f((float) (Math.cos(ang) * r), (float) (Math.sin(ang) * r), 0);
            orientToAxis(a, local, off);
            dir.set(a);
        };
    }

    private static void randomUnit(RandomGenerator rng, Vector3f out) {
        float x = (float) rng.nextGaussian();
        float y = (float) rng.nextGaussian();
        float z = (float) rng.nextGaussian();
        float len = (float) Math.sqrt(x * x + y * y + z * z) + 1e-6f;
        out.set(x / len, y / len, z / len);
    }

    private static void orientToAxis(Vector3f axis, Vector3f local, Vector3f out) {
        Vector3f up = Math.abs(axis.y) < 0.99f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f right = new Vector3f(up).cross(axis).normalize();
        Vector3f fwd = new Vector3f(axis).cross(right).normalize();
        out.set(
                right.x * local.x + fwd.x * local.y + axis.x * local.z,
                right.y * local.x + fwd.y * local.y + axis.y * local.z,
                right.z * local.x + fwd.z * local.y + axis.z * local.z);
    }
}