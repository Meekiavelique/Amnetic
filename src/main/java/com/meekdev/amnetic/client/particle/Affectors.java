package com.meekdev.amnetic.client.particle;

import java.util.function.Supplier;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class Affectors {

    private Affectors() {}

    @FunctionalInterface
    public interface VectorField {
        void sample(Particle p, ParticleContext ctx, Vector3f out);
    }

    public static Affector gravity() {
        return (p, dt, ctx) -> p.vy -= p.gravity * dt;
    }

    public static Affector gravity(float accel) {
        return (p, dt, ctx) -> p.vy -= accel * dt;
    }

    public static Affector drag(float perSecondRetention) {
        float factor = (float) Math.pow(perSecondRetention, ParticleSimulation.STEP);
        return (p, dt, ctx) -> { p.vx *= factor; p.vy *= factor; p.vz *= factor; };
    }

    public static Affector dragPerParticle() {
        return (p, dt, ctx) -> { p.vx *= p.dragStep; p.vy *= p.dragStep; p.vz *= p.dragStep; };
    }

    public static Affector wind(float ax, float ay, float az) {
        return (p, dt, ctx) -> { p.vx += ax * dt; p.vy += ay * dt; p.vz += az * dt; };
    }

    public static Affector wind(Supplier<Vector3fc> windAccel) {
        return (p, dt, ctx) -> {
            Vector3fc w = windAccel.get();
            p.vx += w.x() * dt; p.vy += w.y() * dt; p.vz += w.z() * dt;
        };
    }

    public static Affector turbulence(float scale, float strength) {
        return force((p, ctx, out) -> {
            float t = ctx.time();
            float x = (float) p.x * scale, y = (float) p.y * scale, z = (float) p.z * scale;
            out.set(
                    (float) (Math.sin(y + t) - Math.cos(z + t * 1.1)),
                    (float) (Math.sin(z + t * 0.9) - Math.cos(x + t * 1.2)),
                    (float) (Math.sin(x + t * 1.3) - Math.cos(y + t)))
               .mul(strength);
        });
    }

    public static Affector attractor(double px, double py, double pz, float strength) {
        return force((p, ctx, out) -> {
            out.set((float) (px - p.x), (float) (py - p.y), (float) (pz - p.z));
            float len = out.length();
            if (len > 1e-4f) out.mul(strength / len);
            else out.set(0);
        });
    }

    public static Affector vortex(double px, double py, double pz, float axisX, float axisY, float axisZ,
                                  float strength, float inward) {
        Vector3f axis = new Vector3f(axisX, axisY, axisZ);
        if (axis.lengthSquared() < 1e-8f) axis.set(0f, 1f, 0f);
        axis.normalize();
        return force((p, ctx, out) -> {
            float rx = (float) (p.x - px), ry = (float) (p.y - py), rz = (float) (p.z - pz);
            float along = rx * axis.x + ry * axis.y + rz * axis.z;
            float radx = rx - axis.x * along, rady = ry - axis.y * along, radz = rz - axis.z * along;
            float tx = axis.y * radz - axis.z * rady;
            float ty = axis.z * radx - axis.x * radz;
            float tz = axis.x * rady - axis.y * radx;
            out.set(tx * strength - radx * inward, ty * strength - rady * inward, tz * strength - radz * inward);
        });
    }

    public static Affector vortex(double px, double py, double pz, float strength) {
        return vortex(px, py, pz, 0f, 1f, 0f, strength, 0f);
    }

    public static Affector force(VectorField field) {
        Vector3f tmp = new Vector3f();
        return (p, dt, ctx) -> {
            tmp.set(0);
            field.sample(p, ctx, tmp);
            p.vx += tmp.x * dt; p.vy += tmp.y * dt; p.vz += tmp.z * dt;
        };
    }
}
