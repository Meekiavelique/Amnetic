package com.meekdev.amnetic.client.camera.internal;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.amnetic.client.camera.CameraDirector;
import com.meekdev.amnetic.client.camera.CameraFrame;
import com.meekdev.amnetic.client.camera.CameraModifier;
import com.meekdev.amnetic.client.anim.Easing;
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
    private float overrideRoll;
    private boolean overrideKeepsOffsets;

    private boolean poseActive;
    private Vec3 posePos;
    private float poseYaw, posePitch, poseRoll;
    private boolean poseFovSet;
    private float poseFov;

    private final Vector3f rawWorld = new Vector3f();
    private float rawPitch, rawYaw, rawRoll, rawFov;

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
        overrideRoll = 0f;
        overrideKeepsOffsets = false;

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
            for (int i = modifiers.size() - 1; i >= 0; i--) {
                if (modifiers.get(i).finished()) modifiers.remove(i);
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

        // a director is a takeover and wins, a held pose only applies when none is running
        if (!override && poseActive) {
            override = true;
            overrideKeepsOffsets = true;
            overridePos = posePos;
            overrideYaw = poseYaw;
            overridePitch = posePitch;
            overrideRoll = poseRoll;
            if (poseFovSet) {
                overrideFovSet = true;
                overrideFov = poseFov;
            }
        }
        if (!overrideFovSet && poseFovSet) {
            overrideFovSet = true;
            overrideFov = poseFov;
        }
    }

    public void setPose(Vec3 position, float yaw, float pitch, float roll) {
        poseActive = true;
        posePos = position;
        poseYaw = yaw;
        posePitch = pitch;
        poseRoll = roll;
    }

    public void clearPose() {
        poseActive = false;
        posePos = null;
        poseFovSet = false;
    }

    public boolean hasPose() {
        return poseActive;
    }

    public void setPoseFov(float fov) {
        poseFovSet = true;
        poseFov = fov;
    }

    public void clearPoseFov() {
        poseFovSet = false;
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

    public float overrideRoll() {
        return overrideRoll;
    }

    // a director replaces the shot outright, a held pose still takes shake and kick on top
    public boolean overrideKeepsOffsets() {
        return overrideKeepsOffsets;
    }

    // a lens can be held without a pose: the game still places the camera and only the field of view is set
    public boolean hasFovOverride() {
        return overrideFovSet;
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

    public void clear() {
        worldOffset.zero();
        localOffset.zero();
        rawWorld.zero();
        rawPitch = rawYaw = rawRoll = rawFov = 0f;
        pitchOffset = yawOffset = rollOffset = fovOffset = 0f;
        modifiers.clear();
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
