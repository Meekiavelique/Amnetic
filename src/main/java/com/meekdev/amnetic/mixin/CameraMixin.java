package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.camera.internal.CameraController;
import com.meekdev.amnetic.client.camera.internal.Orthographic;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
//? if >=26.1 {
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix4fc;
//?} else if >=1.21.5 {
/*import com.meekdev.amnetic.client.compat.NaturalFov;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
*///?} else {
/*import com.meekdev.amnetic.client.compat.NaturalFov;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
*///?}
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
    //? if >=26.1 {
    @Shadow private float fov;
    @Shadow private float depthFar;
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private Matrix4f cachedViewRotMatrix;
    @Shadow private void setupPerspective(float near, float far, float fov, float width, float height) { throw new AssertionError(); }
    @Shadow private void prepareCullFrustum(Matrix4fc viewRotation, Matrix4f projection, Vec3 position) { throw new AssertionError(); }
    @Shadow private Matrix4f createProjectionMatrixForCulling() { throw new AssertionError(); }
    @Shadow public abstract Matrix4f getViewRotationMatrix(Matrix4f dest);
    @Shadow private int matrixPropertiesDirty;
    //?}

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow protected abstract void setPosition(Vec3 position);

    //? if >=26.1 {
    @Inject(method = "createProjectionMatrixForCulling", at = @At("HEAD"), cancellable = true)
    private void amnetic$cullOrthographic(CallbackInfoReturnable<Matrix4f> cir) {
        if (Orthographic.active() && amnetic$isMainCamera()) cir.setReturnValue(Orthographic.matrix(new Matrix4f()));
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void amnetic$applyCameraOffsets(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!amnetic$isMainCamera()) return;

        CameraController c = CameraController.INSTANCE;
        c.frame(position, yRot, xRot, fov);

        // the projection was already built from the fov field before this ran, and nothing reads getFov to
        // build it again: a lens held or punched has to go into the field and the projection has to be made
        // over, or it changes a number nobody draws with
        float lens = c.hasFovOverride()
                ? c.overrideFov() + (!c.hasOverride() || c.overrideKeepsOffsets() ? c.fovOffset() : 0f)
                : fov + c.fovOffset();
        if (lens != fov) {
            fov = Math.max(1f, Math.min(179f, lens));
            setupPerspective(0.05f, depthFar, fov, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
            prepareCullFrustum(getViewRotationMatrix(cachedViewRotMatrix), createProjectionMatrixForCulling(), position);
        }
    //?} else if >=1.21.5 {
    /*@Inject(method = "setup", at = @At("TAIL"))
    private void amnetic$applyCameraOffsets(Level level, Entity entity, boolean detached, boolean mirrored,
                                            float partialTick, CallbackInfo ci) {
        if (!amnetic$isMainCamera()) return;

        Minecraft mc = Minecraft.getInstance();
        float naturalFov = ((NaturalFov) mc.gameRenderer).amnetic$naturalFov(
                (Camera) (Object) this, VanillaCompat.partialTick(true));
        CameraController c = CameraController.INSTANCE;
        c.frame(position, yRot, xRot, naturalFov);
    *///?} else {
    /*@Inject(method = "setup", at = @At("TAIL"))
    private void amnetic$applyCameraOffsets(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                            float partialTick, CallbackInfo ci) {
        if (!amnetic$isMainCamera()) return;

        Minecraft mc = Minecraft.getInstance();
        float naturalFov = ((NaturalFov) mc.gameRenderer).amnetic$naturalFov(
                (Camera) (Object) this, VanillaCompat.partialTick(true));
        CameraController c = CameraController.INSTANCE;
        c.frame(position, yRot, xRot, naturalFov);
    *///?}

        if (c.hasOverride()) {
            setRotation(c.overrideYaw(), c.overridePitch());
            setPosition(c.overridePosition());
            if (!c.overrideKeepsOffsets()) return;
        }

        if (c.hasRotationOffset()) {
            float newYaw = yRot + c.yawOffset();
            float newPitch = Math.max(-90f, Math.min(90f, xRot + c.pitchOffset()));
            setRotation(newYaw, newPitch);
        }

        // after the last setRotation, which would otherwise rebuild the basis without it
        float roll = c.rollOffset() + c.overrideRoll();
        if (roll != 0f) amnetic$applyRoll((float) Math.toRadians(roll));

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
        //? if >=26.1 {
        matrixPropertiesDirty = -1; // force view-rotation matrices to rebuild with the roll
        //?}
    }

    //? if >=26.1 {
    @Inject(method = "getViewRotationMatrix", at = @At("HEAD"), cancellable = true)
    private void amnetic$reflectViewRotation(Matrix4f dest, CallbackInfoReturnable<Matrix4f> cir) {
        Matrix4f reflected = CaptureManager.INSTANCE.currentCaptureViewRotation();
        if (reflected != null) cir.setReturnValue(dest.set((Matrix4fc) reflected));
    }
    //?}

    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void amnetic$detachedDuringCapture(CallbackInfoReturnable<Boolean> cir) {
        if (CaptureManager.INSTANCE.isCapturing()) cir.setReturnValue(true);
    }

    private boolean amnetic$isMainCamera() {
        var gr = Minecraft.getInstance().gameRenderer;
        return gr != null && (Camera) (Object) this == gr.getMainCamera();
    }
}
