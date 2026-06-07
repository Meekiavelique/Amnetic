package com.meekdev.amnetic.client.camera;

import net.minecraft.world.phys.Vec3;

public record Ray(Vec3 origin, Vec3 direction) {

    public Ray {
        direction = direction.normalize();
    }

    public Vec3 at(double distance) {
        return origin.add(direction.scale(distance));
    }

    public Vec3 end(double maxDistance) {
        return at(maxDistance);
    }
}
