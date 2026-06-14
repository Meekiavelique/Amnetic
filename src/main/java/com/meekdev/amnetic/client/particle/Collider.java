package com.meekdev.amnetic.client.particle;

@FunctionalInterface
public interface Collider {
    void resolve(Particle p, double ox, double oy, double oz, float dt, ParticleContext ctx);
}
