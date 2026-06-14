package com.meekdev.amnetic.client.particle;

public final class CollisionResponse {

    final float restitution;
    final float friction;
    final boolean kill;
    final CollisionCallback callback;

    private CollisionResponse(float restitution, float friction, boolean kill, CollisionCallback callback) {
        this.restitution = restitution;
        this.friction = friction;
        this.kill = kill;
        this.callback = callback;
    }

    public static CollisionResponse bounce(float restitution) {
        return new CollisionResponse(clamp01(restitution), 0f, false, null);
    }

    public static CollisionResponse bounce(float restitution, float friction) {
        return new CollisionResponse(clamp01(restitution), clamp01(friction), false, null);
    }

    public static CollisionResponse slide(float friction) {
        return new CollisionResponse(0f, clamp01(friction), false, null);
    }

    public static CollisionResponse stop() {
        return new CollisionResponse(0f, 1f, false, null);
    }

    public static CollisionResponse die() {
        return new CollisionResponse(0f, 0f, true, null);
    }

    public CollisionResponse withRestitution(float v) {
        return new CollisionResponse(clamp01(v), friction, kill, callback);
    }

    public CollisionResponse withFriction(float v) {
        return new CollisionResponse(restitution, clamp01(v), kill, callback);
    }

    public CollisionResponse killOnContact() {
        return new CollisionResponse(restitution, friction, true, callback);
    }

    public CollisionResponse onHit(CollisionCallback cb) {
        return new CollisionResponse(restitution, friction, kill, cb);
    }

    void apply(Particle p, float nx, float ny, float nz, boolean firstContact, ParticleContext ctx) {
        double vn = p.vx * nx + p.vy * ny + p.vz * nz;
        if (vn < 0) {
            double tx = p.vx - vn * nx;
            double ty = p.vy - vn * ny;
            double tz = p.vz - vn * nz;
            double f = 1f - friction;
            p.vx = tx * f - restitution * vn * nx;
            p.vy = ty * f - restitution * vn * ny;
            p.vz = tz * f - restitution * vn * nz;
        }
        if (firstContact && callback != null) callback.onHit(p, nx, ny, nz, ctx);
        if (kill) p.age = p.life;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }
}
