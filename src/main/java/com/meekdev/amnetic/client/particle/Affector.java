package com.meekdev.amnetic.client.particle;

@FunctionalInterface
public interface Affector {
    void apply(Particle p, float dt, ParticleContext ctx);
}
