package com.meekdev.amnetic.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

public final class ParticleContext {

    private float time;
    private ClientLevel world;
    private Vec3 cameraPos;

    ParticleContext() {}

    void set(float time, ClientLevel world, Vec3 cameraPos) {
        this.time = time;
        this.world = world;
        this.cameraPos = cameraPos;
    }

    public float time() { return time; }

    public ClientLevel world() { return world; }

    public Vec3 cameraPos() { return cameraPos; }
}
