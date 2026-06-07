package com.meekdev.amnetic.client.camera.internal;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.amnetic.client.camera.CameraDirector;
import com.meekdev.amnetic.client.camera.CameraFrame;
import com.meekdev.amnetic.client.camera.CameraModifier;
import com.meekdev.amnetic.client.particle.Easing;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class CameraController {

    public static final CameraController INSTANCE = new CameraController();

    private static final float MAX_FRAME_DT = 0.1f;

    private CameraController() {}

    private long lastNano;

    private final Vector3f worldOffset = new Vector3f();
    private final Vector3f localOffset = new Vector3f();
    private float pitchOffset, yawOffset, rollOffset;
    private float fovOffset;

    private boolean override;
    private Vec3 overridePos;
    private float overrideYaw, overridePitch;
    private boolean overrideFovSet;
    private float overrideFov;

    private final Vector3f rawWorld = new Vector3f();
    private float rawPitch, rawYaw, rawRoll, rawFov;

    private final List<Impulse> impulses = new ArrayList<>();

    private float trauma;
    private float traumaClock;
    private float shakePosScale = 0.18f;   // blocks at full trauma
    private float shakeRotScale = 4.0f;    // degrees at full trauma
    private float shakeDecayPerS = 1.0f;   // trauma drained per second

    private final Vector3f springPos = new Vector3f();
    private final Vector3f springVel = new Vector3f();
    private final Vector3f springTarget = new Vector3f();
    private boolean springActive;
    private float springStiffness = 120f;
    private float springDamping = 18f;

    private final List<CameraModifier> modifiers = new ArrayList<>();
    private final CameraFrame frame = new CameraFrame(this);
    private float frameClock; // accumulated render seconds, handed to modifiers

    private Director director;

    public void frame(Vec3 naturalPos, float naturalYaw, float naturalPitch, float naturalFov) {
        long now = System.nanoTime();
        float dt = lastNano == 0L ? 0f : (now - lastNano) / 1.0e9f;
        if (dt > MAX_FRAME_DT) dt = MAX_FRAME_DT;
        lastNano = now;

        worldOffset.zero();
        localOffset.zero();
        pitchOffset = yawOffset = rollOffset = 0f;
        fovOffset = 0f;
        override = false;
        overrideFovSet = false;

        worldOffset.add(rawWorld);
        pitchOffset += rawPitch;
        yawOffset += rawYaw;
        rollOffset += rawRoll;
        fovOffset += rawFov;
        rawWorld.zero();
        rawPitch = rawYaw = rawRoll = rawFov = 0f;

        if (!modifiers.isEmpty()) {
            frameClock += dt;
            frame.begin(dt, frameClock, naturalPos, naturalYaw, naturalPitch, naturalFov);
            for (int i = 0; i < modifiers.size(); i++) {
                modifiers.get(i).modify(frame);
            }
        }

        for (int i = impulses.size() - 1; i >= 0; i--) {
            Impulse imp = impulses.get(i);
            imp.advance(dt);
            imp.accumulate(this);
            if (imp.dead()) impulses.remove(i);
        }

        if (trauma > 0f) {
            traumaClock += dt;
            float s = trauma * trauma;
            localOffset.x += noise(traumaClock, 1) * s * shakePosScale;
            localOffset.y += noise(traumaClock, 2) * s * shakePosScale;
            rollOffset += noise(traumaClock, 3) * s * shakeRotScale;
            pitchOffset += noise(traumaClock, 4) * s * shakeRotScale * 0.5f;
            yawOffset += noise(traumaClock, 5) * s * shakeRotScale * 0.5f;
            trauma = Math.max(0f, trauma - shakeDecayPerS * dt);
        }

        if (springActive) {
            integrateSpring(dt);
            worldOffset.add(springPos);
            if (springTarget.lengthSquared() < 1.0e-8f
                    && springPos.lengthSquared() < 1.0e-7f
                    && springVel.lengthSquared() < 1.0e-6f) {
                springActive = false;
                springPos.zero();
                springVel.zero();
            }
        }

        if (director != null) {
            director.advance(dt);
            Director.Pose pose = director.pose(naturalPos, naturalYaw, naturalPitch, naturalFov);
            override = true;
            overridePos = pose.pos;
            overrideYaw = pose.yaw;
            overridePitch = pose.pitch;
            if (pose.hasFov) {
                overrideFovSet = true;
                overrideFov = pose.fov;
            }
            if (director.done()) director = null;
        }
    }

    private void integrateSpring(float dt) {
        int steps = Math.max(1, (int) Math.ceil(dt / 0.008f));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            springVel.x += (-springStiffness * (springPos.x - springTarget.x) - springDamping * springVel.x) * h;
            springVel.y += (-springStiffness * (springPos.y - springTarget.y) - springDamping * springVel.y) * h;
            springVel.z += (-springStiffness * (springPos.z - springTarget.z) - springDamping * springVel.z) * h;
            springPos.fma(h, springVel);
        }
    }

    private static float noise(float t, int seed) {
        float a = (float) Math.sin(t * 17.0 + seed * 1.7);
        float b = (float) Math.sin(t * 31.3 + seed * 4.1);
        return a * 0.6f + b * 0.4f;
    }

    void addWorld(float x, float y, float z) {
        worldOffset.add(x, y, z);
    }

    void addRotation(float pitch, float yaw, float roll) {
        pitchOffset += pitch;
        yawOffset += yaw;
        rollOffset += roll;
    }

    void addFov(float fov) {
        fovOffset += fov;
    }

    public void contributeWorld(double x, double y, double z) {
        worldOffset.add((float) x, (float) y, (float) z);
    }

    public void contributeLocal(double right, double up, double forward) {
        localOffset.add((float) right, (float) up, (float) forward);
    }

    public void contributeRotation(float pitch, float yaw, float roll) {
        pitchOffset += pitch;
        yawOffset += yaw;
        rollOffset += roll;
    }

    public void contributeFov(float fov) {
        fovOffset += fov;
    }

    public boolean hasOverride() {
        return override;
    }

    public Vec3 overridePosition() {
        return overridePos;
    }

    public float overrideYaw() {
        return overrideYaw;
    }

    public float overridePitch() {
        return overridePitch;
    }

    public boolean hasFovOverride() {
        return override && overrideFovSet;
    }

    public float overrideFov() {
        return overrideFov;
    }

    public Vector3f worldOffset() {
        return worldOffset;
    }

    public Vector3f localOffset() {
        return localOffset;
    }

    public float pitchOffset() {
        return pitchOffset;
    }

    public float yawOffset() {
        return yawOffset;
    }

    public float rollOffset() {
        return rollOffset;
    }

    public float fovOffset() {
        return fovOffset;
    }

    public boolean hasRotationOffset() {
        return pitchOffset != 0f || yawOffset != 0f || rollOffset != 0f;
    }

    public boolean hasPositionOffset() {
        return worldOffset.lengthSquared() > 0f || localOffset.lengthSquared() > 0f;
    }

    public void addPositionOffset(double x, double y, double z) {
        rawWorld.add((float) x, (float) y, (float) z);
    }

    public void addRotationOffset(float pitch, float yaw, float roll) {
        rawPitch += pitch;
        rawYaw += yaw;
        rawRoll += roll;
    }

    public void addFovOffset(float fov) {
        rawFov += fov;
    }

    public void impulse(Vector3f posDelta, float pitch, float yaw, float roll, float fov,
                        float duration, Easing easing) {
        impulses.add(new Impulse(posDelta, pitch, yaw, roll, fov, duration, easing));
    }

    public void shake(float traumaToAdd) {
        trauma = Math.min(1f, trauma + traumaToAdd);
    }

    public void shake(float traumaToAdd, float posScale, float rotScale) {
        this.shakePosScale = posScale;
        this.shakeRotScale = rotScale;
        shake(traumaToAdd);
    }

    public void shakeDecay(float perSecond) {
        this.shakeDecayPerS = Math.max(1.0e-3f, perSecond);
    }

    public void springTo(double x, double y, double z, float stiffness, float damping) {
        this.springTarget.set((float) x, (float) y, (float) z);
        this.springStiffness = stiffness;
        this.springDamping = damping;
        this.springActive = true;
    }

    public void clear() {
        worldOffset.zero();
        localOffset.zero();
        rawWorld.zero();
        rawPitch = rawYaw = rawRoll = rawFov = 0f;
        pitchOffset = yawOffset = rollOffset = fovOffset = 0f;
        impulses.clear();
        trauma = 0f;
        springActive = false;
        springPos.zero();
        springVel.zero();
        springTarget.zero();
    }

    public void addModifier(CameraModifier modifier) {
        if (modifier != null && !modifiers.contains(modifier)) modifiers.add(modifier);
    }

    public void removeModifier(CameraModifier modifier) {
        modifiers.remove(modifier);
    }

    public void clearModifiers() {
        modifiers.clear();
    }

    public boolean directorActive() {
        return director != null;
    }

    public void moveTo(Vec3 target, Vec3 lookAt, Float fov, float duration, Easing easing) {
        director = Director.move(camPos(), camYaw(), camPitch(), camFov(),
                target, lookAt, fov, duration, easing);
    }

    public void orbit(Vec3 center, double radius, float speedDegPerS) {
        director = Director.orbit(camPos(), camYaw(), camPitch(), camFov(),
                center, radius, speedDegPerS);
    }

    public void followPath(List<CameraDirector.Keyframe> keys, Easing easing) {
        director = Director.path(camPos(), camYaw(), camPitch(), camFov(), keys, easing);
    }

    public void lockTo(Supplier<Vec3> target) {
        director = Director.lock(camPos(), camYaw(), camPitch(), camFov(), target);
    }

    public void release(float duration) {
        if (director == null) return;
        director.release(duration, camPos(), camYaw(), camPitch(), camFov());
    }

    private static Vec3 camPos() {
        return AmneticCamera.position();
    }

    private static float camYaw() {
        return AmneticCamera.yaw();
    }

    private static float camPitch() {
        return AmneticCamera.pitch();
    }

    private static float camFov() {
        return AmneticCamera.fov();
    }
}
