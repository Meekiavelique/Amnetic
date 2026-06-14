package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.camera.internal.CameraController;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private Vec3 position;
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private float xRot;
    @Shadow private float yRot;
    @Shadow private float fov;
    @Shadow private int matrixPropertiesDirty;

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow protected abstract void setPosition(Vec3 position);

    @Inject(method = "update", at = @At("TAIL"))
    private void amnetic$applyCameraOffsets(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!amnetic$isMainCamera()) return;

        CameraController c = CameraController.INSTANCE;
        c.frame(position, yRot, xRot, fov);

        if (c.hasOverride()) {
            setRotation(c.overrideYaw(), c.overridePitch());
            setPosition(c.overridePosition());
            return;
        }

        if (c.hasRotationOffset()) {
            float newYaw = yRot + c.yawOffset();
            float newPitch = Math.max(-90f, Math.min(90f, xRot + c.pitchOffset()));
            setRotation(newYaw, newPitch);

            float roll = c.rollOffset();
            if (roll != 0f) amnetic$applyRoll((float) Math.toRadians(roll));
        }

        if (c.hasPositionOffset()) {
            Vector3f w = c.worldOffset();
            Vector3f l = c.localOffset(); // (right, up, forward)
            // right = -left; combine the local frame into a world delta using the (now rotated) basis
            double dx = w.x + (-left.x) * l.x + up.x * l.y + forwards.x * l.z;
            double dy = w.y + (-left.y) * l.x + up.y * l.y + forwards.y * l.z;
            double dz = w.z + (-left.z) * l.x + up.z * l.y + forwards.z * l.z;
            setPosition(position.add(dx, dy, dz));
        }
    }

    private void amnetic$applyRoll(float radians) {
        rotation.rotateZ(radians);
        up.rotateAxis(radians, forwards.x, forwards.y, forwards.z);
        left.rotateAxis(radians, forwards.x, forwards.y, forwards.z);
        matrixPropertiesDirty = -1; // force view-rotation matrices to rebuild with the roll
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void amnetic$applyFovOffset(CallbackInfoReturnable<Float> cir) {
        if (!amnetic$isMainCamera()) return;

        CameraController c = CameraController.INSTANCE;
        if (c.hasFovOverride()) {
            cir.setReturnValue(c.overrideFov());
        } else if (c.fovOffset() != 0f) {
            cir.setReturnValue(cir.getReturnValueF() + c.fovOffset());
        }
    }

    @Inject(method = "getViewRotationMatrix", at = @At("HEAD"), cancellable = true)
    private void amnetic$reflectViewRotation(Matrix4f dest, CallbackInfoReturnable<Matrix4f> cir) {
        Matrix4f reflected = CaptureManager.INSTANCE.currentCaptureViewRotation();
        if (reflected != null) cir.setReturnValue(dest.set((Matrix4fc) reflected));
    }

    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void amnetic$detachedDuringCapture(CallbackInfoReturnable<Boolean> cir) {
        if (CaptureManager.INSTANCE.isCapturing()) cir.setReturnValue(true);
    }

    private boolean amnetic$isMainCamera() {
        var gr = Minecraft.getInstance().gameRenderer;
        return gr != null && (Camera) (Object) this == gr.getMainCamera();
    }
}
