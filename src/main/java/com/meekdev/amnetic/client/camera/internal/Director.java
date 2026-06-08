package com.meekdev.amnetic.client.camera.internal;

import com.meekdev.amnetic.client.anim.Easing;
import com.meekdev.amnetic.client.anim.Interpolators;
import com.meekdev.amnetic.client.camera.CameraDirector;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.phys.Vec3;

public final class Director {

    public static final class Pose {
        public Vec3 pos;
        public float yaw, pitch;
        public boolean hasFov;
        public float fov;
    }

    private enum Mode { MOVE, ORBIT, PATH, LOCK }

    private final Mode mode;
    private final Easing easing;
    private final float duration;   // <= 0 means indefinite
    private float elapsed;

    // captured start pose (for MOVE / blends)
    private final Vec3 startPos;
    private final float startYaw, startPitch, startFov;

    // MOVE
    private Vec3 endPos;
    private float endYaw, endPitch;
    private boolean endFovSet;
    private float endFov;

    // ORBIT
    private Vec3 center;
    private double radius;
    private float speedDegPerS;
    private float orbitHeight;
    private float orbitAngle;

    // PATH
    private List<CameraDirector.Keyframe> keys;

    // LOCK
    private Supplier<Vec3> lockTarget;

    // release blend
    private boolean releasing;
    private float releaseElapsed;
    private float releaseDuration;
    private final Pose releaseStart = new Pose();
    private boolean done;

    private final Pose scratch = new Pose();

    private Director(Mode mode, Easing easing, float duration,
                     Vec3 startPos, float startYaw, float startPitch, float startFov) {
        this.mode = mode;
        this.easing = easing != null ? easing : Easing.SMOOTH;
        this.duration = duration;
        this.startPos = startPos;
        this.startYaw = startYaw;
        this.startPitch = startPitch;
        this.startFov = startFov;
    }

    public static Director move(Vec3 start, float startYaw, float startPitch, float startFov,
                                Vec3 endPos, Vec3 lookAt, Float fov, float duration, Easing easing) {
        Director d = new Director(Mode.MOVE, easing, Math.max(1.0e-3f, duration),
                start, startYaw, startPitch, startFov);
        d.endPos = endPos;
        float[] yp = yawPitchToward(endPos, lookAt);
        d.endYaw = unwrap(startYaw, yp[0]);
        d.endPitch = yp[1];
        if (fov != null) {
            d.endFovSet = true;
            d.endFov = fov;
        }
        return d;
    }

    public static Director orbit(Vec3 start, float startYaw, float startPitch, float startFov,
                                 Vec3 center, double radius, float speedDegPerS) {
        Director d = new Director(Mode.ORBIT, Easing.LINEAR, -1f,
                start, startYaw, startPitch, startFov);
        d.center = center;
        d.radius = radius;
        d.speedDegPerS = speedDegPerS;
        d.orbitHeight = (float) (start.y - center.y);
        // begin at the angle the camera currently sits at, for a seamless entry
        d.orbitAngle = (float) Math.toDegrees(Math.atan2(start.z - center.z, start.x - center.x));
        return d;
    }

    public static Director path(Vec3 start, float startYaw, float startPitch, float startFov,
                                List<CameraDirector.Keyframe> keys, Easing easing) {
        float dur = keys.isEmpty() ? 0f : keys.get(keys.size() - 1).time();
        Director d = new Director(Mode.PATH, easing, dur, start, startYaw, startPitch, startFov);
        d.keys = keys;
        return d;
    }

    public static Director lock(Vec3 start, float startYaw, float startPitch, float startFov,
                                Supplier<Vec3> target) {
        Director d = new Director(Mode.LOCK, Easing.SMOOTH, -1f,
                start, startYaw, startPitch, startFov);
        d.lockTarget = target;
        return d;
    }

    public void advance(float dt) {
        if (releasing) {
            releaseElapsed += dt;
            if (releaseElapsed >= releaseDuration) done = true;
            return;
        }
        elapsed += dt;
        if (mode == Mode.ORBIT) {
            orbitAngle += speedDegPerS * dt;
        }
    }

    public boolean done() {
        return done;
    }

    public Pose pose(Vec3 naturalPos, float naturalYaw, float naturalPitch, float naturalFov) {
        Pose p = rawPose(naturalPos, naturalYaw, naturalPitch, naturalFov);
        if (!releasing) return p;

        float t = Interpolators.clamp01(releaseElapsed / releaseDuration);
        float e = easing.apply(t);
        scratch.pos = Interpolators.lerp(releaseStart.pos, naturalPos, e);
        scratch.yaw = Interpolators.lerpAngle(releaseStart.yaw, naturalYaw, e);
        scratch.pitch = Interpolators.lerp(releaseStart.pitch, naturalPitch, e);
        scratch.hasFov = true;
        scratch.fov = Interpolators.lerp(releaseStart.hasFov ? releaseStart.fov : naturalFov, naturalFov, e);
        return scratch;
    }

    private Pose rawPose(Vec3 naturalPos, float naturalYaw, float naturalPitch, float naturalFov) {
        scratch.hasFov = false;
        switch (mode) {
            case MOVE -> {
                float t = duration <= 0 ? 1f : Interpolators.clamp01(elapsed / duration);
                float e = easing.apply(t);
                scratch.pos = Interpolators.lerp(startPos, endPos, e);
                scratch.yaw = Interpolators.lerpAngle(startYaw, endYaw, e);
                scratch.pitch = Interpolators.lerp(startPitch, endPitch, e);
                if (endFovSet) {
                    scratch.hasFov = true;
                    scratch.fov = Interpolators.lerp(startFov, endFov, e);
                }
            }
            case ORBIT -> {
                double a = Math.toRadians(orbitAngle);
                scratch.pos = new Vec3(
                        center.x + Math.cos(a) * radius,
                        center.y + orbitHeight,
                        center.z + Math.sin(a) * radius);
                float[] yp = yawPitchToward(scratch.pos, center);
                scratch.yaw = yp[0];
                scratch.pitch = yp[1];
            }
            case PATH -> samplePath(naturalYaw);
            case LOCK -> {
                Vec3 target = lockTarget.get();
                scratch.pos = naturalPos;
                float[] yp = yawPitchToward(naturalPos, target);
                scratch.yaw = yp[0];
                scratch.pitch = yp[1];
            }
        }
        return scratch;
    }

    private void samplePath(float fallbackYaw) {
        if (keys == null || keys.isEmpty()) {
            scratch.pos = startPos;
            scratch.yaw = startYaw;
            scratch.pitch = startPitch;
            return;
        }
        float time = clamp(elapsed, 0f, keys.get(keys.size() - 1).time());
        CameraDirector.Keyframe a = keys.get(0);
        CameraDirector.Keyframe b = a;
        for (int i = 1; i < keys.size(); i++) {
            b = keys.get(i);
            if (time <= b.time()) break;
            a = b;
        }
        float span = b.time() - a.time();
        float local = span <= 1.0e-4f ? 1f : (time - a.time()) / span;
        float e = easing.apply(local);
        scratch.pos = Interpolators.lerp(a.position(), b.position(), e);
        Vec3 lookAt = Interpolators.lerp(a.lookAt(), b.lookAt(), e);
        float[] yp = yawPitchToward(scratch.pos, lookAt);
        scratch.yaw = yp[0];
        scratch.pitch = yp[1];
    }

    public void release(float durationSeconds, Vec3 naturalPos, float naturalYaw,
                        float naturalPitch, float naturalFov) {
        if (releasing) return;
        Pose cur = rawPose(naturalPos, naturalYaw, naturalPitch, naturalFov);
        releaseStart.pos = cur.pos;
        releaseStart.yaw = cur.yaw;
        releaseStart.pitch = cur.pitch;
        releaseStart.hasFov = cur.hasFov;
        releaseStart.fov = cur.fov;
        releaseDuration = Math.max(1.0e-3f, durationSeconds);
        releaseElapsed = 0f;
        releasing = true;
        if (releaseDuration <= 1.0e-3f) done = true;
    }

    private static float[] yawPitchToward(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0e-6) return new float[]{0f, 0f};
        Vec3 n = d.scale(1.0 / len);
        float yaw = (float) Math.toDegrees(Math.atan2(-n.x, n.z));
        float pitch = (float) -Math.toDegrees(Math.asin(clamp(n.y, -1.0, 1.0)));
        return new float[]{yaw, pitch};
    }

    private static float unwrap(float reference, float target) {
        float delta = ((target - reference) % 360f + 540f) % 360f - 180f;
        return reference + delta;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
