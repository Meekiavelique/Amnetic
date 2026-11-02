package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.camera.internal.FrameView;
import com.meekdev.amnetic.client.camera.internal.Orthographic;
import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.model.HandModels;
import com.meekdev.amnetic.client.model.ViewModels;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderLayer;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.pipeline.internal.LayerSystem;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import com.meekdev.amnetic.client.render.AfterWorldRender;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import com.meekdev.amnetic.client.pipeline.internal.LayerRedirect;
import com.meekdev.amnetic.mixin.accessor.GameRendererAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.meekdev.amnetic.client.taa.Taa;
import com.meekdev.amnetic.client.taa.internal.TaaJitter;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class RenderPearlGameRendererMixin {
    @Shadow @Final private Projection hudProjection;

    @Redirect(method = {"render", "renderLevel", "render3dHud", "renderItemInHand"},
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;",
                    opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private RenderTarget amnetic$redirectTarget(GameRenderer renderer) {
        RenderTarget capture = CaptureManager.INSTANCE.currentCaptureTarget();
        if (capture != null) return capture;
        RenderTarget layer = LayerRedirect.active();
        return layer != null ? layer : ((GameRendererAccessor) renderer).amnetic$getMainRenderTarget();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            //? if >=26.3 {
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel()V"
            //? } else {
            /*target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"
            *///? }
            ))
    private void amnetic$captureScenes(CallbackInfo ci) {
        CaptureManager.INSTANCE.runCaptures((GameRenderer) (Object) this, Minecraft.getInstance().getDeltaTracker());
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    private GpuBufferSlice amnetic$worldProjection(ProjectionMatrixBuffer buffer, Matrix4f projection) {
        if (!CaptureManager.INSTANCE.isCapturing()) {
            if (Orthographic.active()) Orthographic.matrix(projection);
            if (Taa.jitterActive()) {
                var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
                TaaJitter.INSTANCE.beginFrame(target.width, target.height);
                TaaJitter.INSTANCE.applyTo(projection);
            } else TaaJitter.INSTANCE.reset();
            FrameView.INSTANCE.setProjection(projection);
        }
        return buffer.getBuffer(projection);
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void amnetic$skipOutline(CallbackInfoReturnable<Boolean> cir) {
        if (CaptureManager.INSTANCE.isCapturing()) cir.setReturnValue(false);
    }

    //? if >=26.3 {
    @Inject(method = "render3dHud", at = @At("HEAD"), cancellable = true)
    //? } else {
    /*@Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;clearDepthTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;D)V"), cancellable = true)
    *///? }
    private void amnetic$postWorld(CallbackInfo ci) {
        if (CaptureManager.INSTANCE.isCapturing()) { ci.cancel(); return; }
        if (com.meekdev.amnetic.client.compat.IrisCompat.shadersActive()
                && com.meekdev.amnetic.client.compat.IrisCompat.canComposite()) {
            com.meekdev.amnetic.client.render.NativeCommandBatch.begin();
            try {
                com.meekdev.amnetic.client.shadow.internal.ShadowMapPass.INSTANCE.render(com.meekdev.amnetic.client.render.CameraSnapshot.current());
                com.meekdev.amnetic.client.light.internal.DeferredLightingPass.INSTANCE.renderSurface();
                com.meekdev.amnetic.client.light.internal.VolumetricPass.INSTANCE.renderAfterIris();
            } finally {
                com.meekdev.amnetic.client.render.NativeCommandBatch.end();
            }
        }
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.PRE_GUI)
                || PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.POST_RENDER)) {
            PostEffectRegistry.INSTANCE.captureWorldDepthSnapshot(target);
        }
        PostEffectRegistry.INSTANCE.applyAll(RenderPhase.POST_WORLD, VanillaCompat.partialTick(true));
        AfterWorldRender.fire();
        Pipeline.runStage(RenderStage.OVERLAY);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void amnetic$beginHand(CallbackInfo ci) {
        if(!Minecraft.getInstance().options.getCameraType().isFirstPerson())return;
        Matrix4f projection = hudProjection.getMatrix(new Matrix4f());
        HandModels.captureProjection(projection);
        HandModels.beginFrame();
        ViewModels.captureProjection(projection);
        if(LayerSystem.INSTANCE.begin(RenderLayer.HAND))com.meekdev.amnetic.client.render.CameraSnapshot.handProjection(projection);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void amnetic$endHand(CallbackInfo ci) {
        if(!Minecraft.getInstance().options.getCameraType().isFirstPerson())return;
        HandModels.render();
        ViewModels.render();
        LayerSystem.INSTANCE.end(RenderLayer.HAND);
        Pipeline.runStage(RenderStage.AFTER_HAND);
    }

    @org.spongepowered.asm.mixin.injection.ModifyArg(method="renderItemInHand",at=@At(value="INVOKE",target="Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"),index=3)
    private com.mojang.renderpearl.api.textures.GpuTextureView amnetic$handDepth(com.mojang.renderpearl.api.textures.GpuTextureView depth) {
        var target=LayerRedirect.active();return target==null?depth:target.getDepthTextureView();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;clearDepthTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;D)V"))
    private void amnetic$beforeGui(CallbackInfo ci) {
        if (Minecraft.getInstance().level == null) return;
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.POST_RENDER)) {
            PostEffectRegistry.INSTANCE.capturePostRenderDepthSnapshot(target);
        }
        PostEffectRegistry.INSTANCE.applyAll(RenderPhase.PRE_GUI, VanillaCompat.partialTick(true));
        Pipeline.runStage(RenderStage.BEFORE_GUI);
        LayerSystem.INSTANCE.begin(RenderLayer.GUI);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void amnetic$afterGui(CallbackInfo ci) {
        LayerSystem.INSTANCE.end(RenderLayer.GUI);
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (PostEffectRegistry.INSTANCE.hasEnabledEffectInPhase(RenderPhase.POST_RENDER)) {
            PostEffectRegistry.INSTANCE.restorePostRenderDepthSnapshotInto(target);
        }
        PostEffectRegistry.INSTANCE.applyAll(RenderPhase.POST_RENDER, VanillaCompat.partialTick(true));
        Pipeline.runStage(RenderStage.AFTER_GUI);
        com.meekdev.amnetic.client.ui.NativeEditorBackend.render();
    }
}
