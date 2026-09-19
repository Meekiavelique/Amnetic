package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.camera.internal.Orthographic;
import com.meekdev.amnetic.client.camera.internal.FrameView;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderLayer;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.model.HandModels;
import com.meekdev.amnetic.client.model.ViewModels;
import com.meekdev.amnetic.client.pipeline.internal.LayerSystem;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import com.meekdev.amnetic.client.render.AfterWorldRender;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import com.meekdev.amnetic.client.taa.Taa;
import com.meekdev.amnetic.client.taa.internal.TaaJitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
//? if >=26.1 {
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
//?} else if >=1.21.5 {
/*import com.meekdev.amnetic.client.camera.internal.CameraController;
import com.meekdev.amnetic.client.compat.NaturalFov;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
*///?} else {
/*import com.meekdev.amnetic.client.camera.internal.CameraController;
import com.meekdev.amnetic.client.compat.NaturalFov;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.injection.ModifyArg;
*///?}
//? if >=1.21 {
import net.minecraft.client.DeltaTracker;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
*///?}
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
//? if >=26.1 {
public abstract class GameRendererMixin {
//?} else {
/*public abstract class GameRendererMixin implements NaturalFov {
*///?}

    //? if >=26.1 {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void amnetic$skipHandInCapture(CameraRenderState cam, float partialTick, Matrix4fc handProjection, CallbackInfo ci) {
        if (CaptureManager.INSTANCE.isCapturing()) {
            ci.cancel();
            return;
        }
    //?} else if >=1.21.5 {
    /*@Shadow @Final private Camera mainCamera;
    @Shadow @Final private Minecraft minecraft;
    @Shadow private float getFov(Camera camera, float partialTick, boolean worldFov) { throw new AssertionError(); }

    @Unique private boolean amnetic$naturalFovQuery;

    @Override
    public float amnetic$naturalFov(Camera camera, float partialTick) {
        amnetic$naturalFovQuery = true;
        try {
            return getFov(camera, partialTick, true);
        } finally {
            amnetic$naturalFovQuery = false;
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void amnetic$applyLens(Camera camera, float partialTick, boolean worldFov, CallbackInfoReturnable<Float> cir) {
        if (!worldFov || camera != mainCamera || amnetic$naturalFovQuery) return;
        float fov = amnetic$lens(cir.getReturnValueF());
        VanillaCompat.recordWorldFov(fov);
        cir.setReturnValue(fov);
    }

    @Inject(method = "getProjectionMatrixForCulling", at = @At("HEAD"), cancellable = true)
    private void amnetic$cullOrthographic(float fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (Orthographic.active()) cir.setReturnValue(Orthographic.matrix(new Matrix4f()));
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void amnetic$skipHandInCapture(float partialTick, boolean sleeping, Matrix4f viewRotation, CallbackInfo ci) {
        if (CaptureManager.INSTANCE.isCapturing()) {
            ci.cancel();
            return;
        }
        Matrix4f handProjection = new Matrix4f().perspective(
                getFov(mainCamera, partialTick, false) * (float) (Math.PI / 180.0),
                (float) minecraft.getWindow().getWidth() / minecraft.getWindow().getHeight(), 0.05f, 100f);
    *///?} else if >=1.21 {
    /*@Shadow @Final private Camera mainCamera;
    @Shadow private double getFov(Camera camera, float partialTick, boolean worldFov) { throw new AssertionError(); }
    @Shadow public abstract Matrix4f getProjectionMatrix(double fov);

    @Unique private boolean amnetic$naturalFovQuery;

    @Override
    public float amnetic$naturalFov(Camera camera, float partialTick) {
        amnetic$naturalFovQuery = true;
        try {
            return (float) getFov(camera, partialTick, true);
        } finally {
            amnetic$naturalFovQuery = false;
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void amnetic$applyLens(Camera camera, float partialTick, boolean worldFov, CallbackInfoReturnable<Double> cir) {
        if (!worldFov || camera != mainCamera || amnetic$naturalFovQuery) return;
        float fov = amnetic$lens((float) (double) cir.getReturnValue());
        VanillaCompat.recordWorldFov(fov);
        cir.setReturnValue((double) fov);
    }

    @ModifyArg(method = "renderLevel", index = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private Matrix4f amnetic$cullOrthographic(Matrix4f cullProjection) {
        return Orthographic.active() ? Orthographic.matrix(new Matrix4f()) : cullProjection;
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void amnetic$skipHandInCapture(Camera camera, float partialTick, Matrix4f viewRotation, CallbackInfo ci) {
        if (CaptureManager.INSTANCE.isCapturing()) {
            ci.cancel();
            return;
        }
        Matrix4f handProjection = getProjectionMatrix(getFov(camera, partialTick, false));
    *///?} else {
    /*@Shadow @Final private Camera mainCamera;
    @Shadow private double getFov(Camera camera, float partialTick, boolean worldFov) { throw new AssertionError(); }
    @Shadow public abstract Matrix4f getProjectionMatrix(double fov);

    @Unique private boolean amnetic$naturalFovQuery;

    @Override
    public float amnetic$naturalFov(Camera camera, float partialTick) {
        amnetic$naturalFovQuery = true;
        try {
            return (float) getFov(camera, partialTick, true);
        } finally {
            amnetic$naturalFovQuery = false;
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void amnetic$applyLens(Camera camera, float partialTick, boolean worldFov, CallbackInfoReturnable<Double> cir) {
        if (!worldFov || camera != mainCamera || amnetic$naturalFovQuery) return;
        float fov = amnetic$lens((float) (double) cir.getReturnValue());
        VanillaCompat.recordWorldFov(fov);
        cir.setReturnValue((double) fov);
    }

    @ModifyArg(method = "renderLevel", index = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;)V"))
    private Matrix4f amnetic$cullOrthographic(Matrix4f cullProjection) {
        return Orthographic.active() ? Orthographic.matrix(new Matrix4f()) : cullProjection;
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void amnetic$skipHandInCapture(PoseStack poseStack, Camera camera, float partialTick, CallbackInfo ci) {
        if (CaptureManager.INSTANCE.isCapturing()) {
            ci.cancel();
            return;
        }
        Matrix4f handProjection = getProjectionMatrix(getFov(camera, partialTick, false));
    *///?}
        HandModels.captureProjection(handProjection);
        HandModels.beginFrame();
        ViewModels.captureProjection(handProjection);
        LayerSystem.INSTANCE.begin(RenderLayer.HAND);
    }

    //? if <26.1 {
    /*@Unique
    private static float amnetic$lens(float fov) {
        CameraController c = CameraController.INSTANCE;
        float lens = c.hasFovOverride()
                ? c.overrideFov() + (!c.hasOverride() || c.overrideKeepsOffsets() ? c.fovOffset() : 0f)
                : fov + c.fovOffset();
        return lens != fov ? Math.max(1f, Math.min(179f, lens)) : fov;
    }
    *///?}

    @Inject(method = "renderItemInHand", at = @At("TAIL"))
    private void amnetic$endHandLayer(CallbackInfo ci) {
        // draw the custom held model now, same frame as its captured pose, so it tracks view bob 1:1
        HandModels.render();
        // view-space models draw right after the hand, sharing the same FOV/frame
        ViewModels.render();
        LayerSystem.INSTANCE.end(RenderLayer.HAND);
        // here and not at the OVERLAY injection, that one fires before the hand draws
        Pipeline.runStage(RenderStage.AFTER_HAND);
    }

    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    //? if >=26.1 {
                    target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;",
                    //?} else if >=1.21.5 {
                    /*target = "Lnet/minecraft/client/renderer/PerspectiveProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;",
                    *///?} else {
                    /*target = "Lnet/minecraft/client/renderer/GameRenderer;resetProjectionMatrix(Lorg/joml/Matrix4f;)V",
                    *///?}
                    ordinal = 0
            )
    )
    //? if >=26.1 {
    private GpuBufferSlice amnetic$captureBobbedProjection(ProjectionMatrixBuffer buffer, Matrix4f projectionMatrix) {
    //?} else if >=1.21.5 {
    /*private GpuBufferSlice amnetic$captureBobbedProjection(PerspectiveProjectionMatrixBuffer buffer, Matrix4f projectionMatrix) {
    *///?} else {
    /*private void amnetic$captureBobbedProjection(GameRenderer renderer, Matrix4f projectionMatrix) {
    *///?}
        if (!CaptureManager.INSTANCE.isCapturing()) {
            if (Orthographic.active()) Orthographic.matrix(projectionMatrix);
            if (Taa.jitterActive()) {
                var target = Minecraft.getInstance().getMainRenderTarget();
                TaaJitter.INSTANCE.beginFrame(target.width, target.height);
                TaaJitter.INSTANCE.applyTo(projectionMatrix);
            } else {
                TaaJitter.INSTANCE.reset();
            }
            FrameView.INSTANCE.setProjection(projectionMatrix);
        }
        //? if >=1.21.5 {
        return buffer.getBuffer(projectionMatrix);
        //?} else {
        /*renderer.resetProjectionMatrix(projectionMatrix);
        *///?}
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void amnetic$skipOutlineInCapture(CallbackInfoReturnable<Boolean> cir) {
        if (CaptureManager.INSTANCE.isCapturing()) cir.setReturnValue(false);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    //? if >=1.21 {
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
                    //?} else {
                    /*target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V",
                    *///?}
                    shift = At.Shift.BEFORE
            )
    )
    //? if >=1.21 {
    private void amnetic$sceneCapture(DeltaTracker ticker, boolean renderLevel, CallbackInfo ci) {
        if (renderLevel) CaptureManager.INSTANCE.runCaptures((GameRenderer) (Object) this, ticker);
    }
    //?} else {
    /*private void amnetic$sceneCapture(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        if (renderLevel) CaptureManager.INSTANCE.runCaptures((GameRenderer) (Object) this);
    }
    *///?}

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    //? if >=1.21.5 {
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V",
                    shift = At.Shift.BEFORE
                    //?} else if >=1.21 {
                    /*target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER
                    *///?} else {
                    /*target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER
                    *///?}
            )
    )
    private void amnetic$onPostWorldRender(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.PRE_GUI)
                || PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.POST_RENDER)) {
            PostEffectRegistry.INSTANCE.captureWorldDepthSnapshot(mc.getMainRenderTarget());
        }
        PostEffectRegistry.INSTANCE.applyAll(RenderPhase.POST_WORLD, VanillaCompat.partialTick(true));
        AfterWorldRender.fire();
        Pipeline.runStage(RenderStage.OVERLAY);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    //? if >=1.21.5 {
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V",
                    //?} else {
                    /*target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V",
                    ordinal = 0,
                    *///?}
                    shift = At.Shift.BEFORE
            )
    )
    //? if >=1.21 {
    private void amnetic$onPreScreenDepthClear(DeltaTracker ticker, boolean renderLevel, CallbackInfo ci) {
    //?} else {
    /*private void amnetic$onPreScreenDepthClear(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
    *///?}
        if (!renderLevel) return;

        Minecraft mc = Minecraft.getInstance();
        if (PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.POST_RENDER)) {
            PostEffectRegistry.INSTANCE.capturePostRenderDepthSnapshot(mc.getMainRenderTarget());
        }
        PostEffectRegistry.INSTANCE.applyAll(RenderPhase.PRE_GUI, VanillaCompat.partialTick(true));
        Pipeline.runStage(RenderStage.BEFORE_GUI);
        LayerSystem.INSTANCE.begin(RenderLayer.GUI);
    }

    @Inject(method = "render", at = @At("TAIL"))
    //? if >=1.21 {
    private void amnetic$onPostRender(DeltaTracker ticker, boolean renderLevel, CallbackInfo ci) {
    //?} else {
    /*private void amnetic$onPostRender(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
    *///?}
        LayerSystem.INSTANCE.end(RenderLayer.GUI);
        Minecraft mc = Minecraft.getInstance();
        if (PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.POST_RENDER)) {
            PostEffectRegistry.INSTANCE.restorePostRenderDepthSnapshotInto(mc.getMainRenderTarget());
        }
        PostEffectRegistry.INSTANCE.applyAll(RenderPhase.POST_RENDER, VanillaCompat.partialTick(true));
        Pipeline.runStage(RenderStage.AFTER_GUI);
    }
}
