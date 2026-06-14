package com.meekdev.amnetic.client.particle;

@FunctionalInterface
public interface CollisionCallback {
    void onHit(Particle p, float nx, float ny, float nz, ParticleContext ctx);
}
