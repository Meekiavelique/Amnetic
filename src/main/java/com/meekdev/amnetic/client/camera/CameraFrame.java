package com.meekdev.amnetic.client.camera;

import com.meekdev.amnetic.client.camera.internal.CameraController;
import net.minecraft.world.phys.Vec3;

/**
 * Per-frame context handed to a {@link CameraModifier}. Exposes timing and a read-only snapshot of
 * the player-follow camera pose, and collects the modifier's additive contributions.
 *
 * <p>Rotation contributions are in degrees (pitch, yaw, roll); FOV in degrees. Position can be added
 * in world space ({@link #addPosition}) or in view space ({@link #addPositionLocal}: right, up,
 * forward), whichever is more convenient. All contributions accumulate and compose with the built-in
 * effects.
 *
 * <p>Instances are reused across frames and are only valid for the duration of the
 * {@link CameraModifier#modify} call — do not retain a reference.
 */
public final class CameraFrame {

    private final CameraController owner;

    private float dt;
    private float time;
    private Vec3 cameraPos = Vec3.ZERO;
    private float yaw;
    private float pitch;
    private float fov;

    public CameraFrame(CameraController owner) {
        this.owner = owner;
    }

    /** Refresh per-frame inputs. Called by {@link CameraController} before dispatching modifiers. */
    public void begin(float dt, float time, Vec3 cameraPos, float yaw, float pitch, float fov) {
        this.dt = dt;
        this.time = time;
        this.cameraPos = cameraPos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fov = fov;
    }

    // ---- timing & pose ----------------------------------------------------------------------

    /** Seconds elapsed since the previous frame (clamped; frame-rate independent). */
    public float dt() {
        return dt;
    }

    /** Monotonic render clock in seconds — feed this to your noise/oscillators. */
    public float time() {
        return time;
    }

    /** The player-follow camera position this frame, before any offsets are applied. */
    public Vec3 cameraPosition() {
        return cameraPos;
    }

    /** Player-follow yaw in degrees. */
    public float yaw() {
        return yaw;
    }

    /** Player-follow pitch in degrees. */
    public float pitch() {
        return pitch;
    }

    /** Player-follow field of view in degrees. */
    public float fov() {
        return fov;
    }

    // ---- contributions ----------------------------------------------------------------------

    /** Add a world-space position offset (blocks). */
    public void addPosition(Vec3 offset) {
        owner.contributeWorld(offset.x, offset.y, offset.z);
    }

    /** Add a world-space position offset (blocks). */
    public void addPosition(double x, double y, double z) {
        owner.contributeWorld(x, y, z);
    }

    /** Add a view-space position offset (right, up, forward) in blocks. */
    public void addPositionLocal(double right, double up, double forward) {
        owner.contributeLocal(right, up, forward);
    }

    /** Add a rotation offset in degrees. */
    public void addRotation(float pitch, float yaw, float roll) {
        owner.contributeRotation(pitch, yaw, roll);
    }

    /** Add a field-of-view offset in degrees. */
    public void addFov(float fov) {
        owner.contributeFov(fov);
    }
}
