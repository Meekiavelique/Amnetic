package com.meekdev.amnetic.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class Colliders {

    private static final double SKIN = 1e-3;
    private static final double MIN_MOVE = 1e-6;

    private Colliders() {}

    public static Collider world(CollisionResponse response) {
        return new WorldCollider(response);
    }

    public static Collider plane(double y, CollisionResponse response) {
        return new PlaneCollider(y, response);
    }

    private static final class WorldCollider implements Collider {

        private final CollisionResponse response;

        WorldCollider(CollisionResponse response) {
            this.response = response;
        }

        @Override
        public void resolve(Particle p, double ox, double oy, double oz, float dt, ParticleContext ctx) {
            ClientLevel level = ctx.world();
            if (level == null) { p.colliding = false; return; }

            double dx = p.x - ox, dy = p.y - oy, dz = p.z - oz;
            if (dx * dx + dy * dy + dz * dz < MIN_MOVE) { p.colliding = false; return; }

            BlockHitResult hit = level.clip(new ClipContext(
                    new Vec3(ox, oy, oz), new Vec3(p.x, p.y, p.z),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));

            if (hit.getType() == HitResult.Type.MISS) { p.colliding = false; return; }

            Direction face = hit.getDirection();
            float nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
            Vec3 at = hit.getLocation();
            p.x = at.x + nx * SKIN;
            p.y = at.y + ny * SKIN;
            p.z = at.z + nz * SKIN;

            boolean first = !p.colliding;
            p.colliding = true;
            response.apply(p, nx, ny, nz, first, ctx);
        }
    }

    private static final class PlaneCollider implements Collider {

        private final double y;
        private final CollisionResponse response;

        PlaneCollider(double y, CollisionResponse response) {
            this.y = y;
            this.response = response;
        }

        @Override
        public void resolve(Particle p, double ox, double oy, double oz, float dt, ParticleContext ctx) {
            if (p.y >= y || oy < y) { p.colliding = false; return; }
            p.y = y;
            boolean first = !p.colliding;
            p.colliding = true;
            response.apply(p, 0f, 1f, 0f, first, ctx);
        }
    }
}
