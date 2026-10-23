package com.example.camera;

import com.meekdev.amnetic.client.camera.CameraFrame;
import com.meekdev.amnetic.client.camera.CameraModifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class HandheldCamera implements CameraModifier {

    public static final HandheldCamera INSTANCE = new HandheldCamera();

    public boolean enabled = true;

    public float cadence = 0.62f;
    public float bobVertical = 0.055f;
    public float bobHorizontal = 0.045f;
    public float bobRoll = 0.9f;
    public float bobPitch = 0.45f;
    public float sprintBoost = 1.35f;
    public float turnRoll = 0.07f;
    public float maxTurnRoll = 5f;
    public float lateralRoll = 16f;
    public float maxLateralRoll = 4f;
    public float smoothing = 10f;
    public float breathe = 0.4f;

    private float phase, amp, rollSmooth, posX, posY, rotPitch, rotRoll, lastYaw, landDip;
    private boolean hasYaw, lastGround = true;

    private HandheldCamera() {}

    @Override
    public void modify(CameraFrame f) {
        if (!enabled) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;

        float dt = Math.min(f.dt(), 0.1f);
        Vec3 v = p.getDeltaMovement();
        float speed = (float) Math.sqrt(v.x * v.x + v.z * v.z);
        boolean ground = p.onGround();
        float boost = p.isSprinting() ? sprintBoost : 1f;

        float targetAmp = (ground && speed > 0.012f) ? Math.min(1f, speed / 0.16f) : 0f;
        amp += (targetAmp - amp) * Math.min(1f, dt * 8f);

        float distance = speed * dt * 20.0f;
        phase += distance * cadence * 6.2831853f;

        float tVb = (float) Math.sin(phase * 2.0) * bobVertical * amp * boost;
        float tHb = (float) Math.sin(phase) * bobHorizontal * amp * boost;
        float tRoll = (float) Math.sin(phase) * bobRoll * amp * boost;
        float tPitch = Math.abs((float) Math.sin(phase * 2.0)) * bobPitch * amp * boost;

        float idle = 1f - amp;
        tVb += (float) Math.sin(f.time() * 1.4) * 0.010f * idle * breathe;
        tRoll += (float) Math.sin(f.time() * 0.7) * 0.6f * idle * breathe;

        float yaw = f.yaw();
        float leanTarget = 0f;
        if (hasYaw) {
            float rate = dt > 1e-4f ? wrapDeg(yaw - lastYaw) / dt : 0f;
            leanTarget += clamp(rate * turnRoll, -maxTurnRoll, maxTurnRoll);
        }
        lastYaw = yaw;
        hasYaw = true;
        float yawRad = (float) Math.toRadians(yaw);
        float lateral = (float) (v.x * Math.cos(yawRad) + v.z * Math.sin(yawRad));
        leanTarget += clamp(-lateral * lateralRoll, -maxLateralRoll, maxLateralRoll);
        rollSmooth += (leanTarget - rollSmooth) * Math.min(1f, dt * 6f);

        if (ground && !lastGround) landDip = Math.min(0.14f, (float) Math.max(0.0, -v.y) * 0.5f + 0.04f);
        lastGround = ground;
        landDip *= Math.max(0f, 1f - dt * 6f);

        float k = Math.min(1f, dt * smoothing);
        posX += (tHb - posX) * k;
        posY += ((tVb - landDip) - posY) * k;
        rotPitch += (tPitch - rotPitch) * k;
        rotRoll += (tRoll - rotRoll) * k;

        f.addPositionLocal(posX, posY, 0.0);
        f.addRotation(rotPitch, 0f, rotRoll + rollSmooth);
    }

    private static float wrapDeg(float d) { d = (d + 180f) % 360f; if (d < 0f) d += 360f; return d - 180f; }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
