package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.compat.IrisCompat;
import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.LightSettings;
import com.meekdev.amnetic.client.light.LightType;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.minecraft.resources.Identifier;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import java.util.Optional;
//? if >=26.2 {
import com.meekdev.amnetic.client.render.NativeScreenProgram;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
//?}

public final class VolumetricPass {
    public static final VolumetricPass INSTANCE = new VolumetricPass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/light/deferred.vsh");
    private static final Identifier TEMPORAL_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/volumetric/temporal.fsh");
    private static final Identifier UPSCALE_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/volumetric/upscale.fsh");
    private static final float FEEDBACK = 0.9f;

    private ShaderProgram temporal;
    private ShaderProgram upscale;
    private Framebuffer capture;
    private Framebuffer raw;
    private Framebuffer historyA;
    private Framebuffer historyB;
    private boolean usingA;
    private float scale = -1f;
    private boolean initialized;

    private final Matrix4f prevViewProj = new Matrix4f();
    private double prevEyeX;
    private double prevEyeY;
    private double prevEyeZ;
    private boolean historyValid;
    private boolean nativeReversed = true;
    //? if >=26.2 {
    private static final double MAX_EYE_JUMP_SQUARED = 256;
    private static final int WRITE_ALL = 15;
    private NativeScreenProgram nativeTemporal;
    private NativeScreenProgram nativeUpscale;
    private int nativeWidth;
    private int nativeHeight;
    private final FrustumIntersection nativeFrustum = new FrustumIntersection();

    private void renderNative(LightSettings settings, CameraSnapshot cam) {
        nativeFrustum.set(cam.viewProj);
        if (!anyScatteringVisible(cam)) {
            historyValid = false;
            return;
        }
        ensure(settings.volumetricScale());
        var sceneDepth = Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView();
        var rawView = raw.colorTextureView(0);
        if (nativeWidth != raw.width() || nativeHeight != raw.height()) historyValid = false;
        nativeWidth = raw.width();
        nativeHeight = raw.height();
        DeferredLightingPass.INSTANCE.renderVolumetricRaw(raw);
        if (!DeferredLightingPass.INSTANCE.hasNativeScattering()) {
            historyValid = false;
            return;
        }
        boolean reversed = IrisCompat.reverseDepth();
        if (nativeTemporal != null && reversed != nativeReversed) {
            nativeTemporal.close();
            nativeTemporal = null;
            historyValid = false;
        }
        nativeReversed = reversed;
        double dx = cam.eye.x - prevEyeX;
        double dy = cam.eye.y - prevEyeY;
        double dz = cam.eye.z - prevEyeZ;
        if (dx * dx + dy * dy + dz * dz > MAX_EYE_JUMP_SQUARED) historyValid = false;
        ensureNativePrograms();
        Framebuffer resolved = raw;
        if (settings.volumetricTemporal()) {
            resolved = resolveNativeHistory(cam, rawView, sceneDepth);
        } else {
            historyValid = false;
        }
        nativeUpscale.setTexture("Scatter", resolved.colorTextureView(0));
        nativeUpscale.setTexture("DepthSampler", sceneDepth);
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        nativeUpscale.draw(RenderPassDescriptor.builder(() -> "Amnetic volumetric upscale")
                .withColorAttachment(main.getColorTextureView()).build());
        prevViewProj.set(cam.viewProj);
        prevEyeX = cam.eye.x;
        prevEyeY = cam.eye.y;
        prevEyeZ = cam.eye.z;
    }

    private boolean anyScatteringVisible(CameraSnapshot cam) {
        for (var light : LightRegistry.INSTANCE.all()) {
            if (!light.isEnabled() || light.intensity() <= 0 || light.godray() <= 0
                    || light.godrayDensity() <= 0 || light.godraySteps() <= 0) continue;
            float radius = light.range() + Math.max(Math.max(light.areaW(), light.areaH()), light.tubeLen());
            if (light.type() == LightType.DIRECTIONAL) return true;
            float x = (float) (light.x() - cam.eye.x);
            float y = (float) (light.y() - cam.eye.y);
            float z = (float) (light.z() - cam.eye.z);
            if (nativeFrustum.testSphere(x, y, z, radius)) return true;
        }
        return false;
    }

    private void ensureNativePrograms() {
        if (nativeTemporal == null) {
            nativeTemporal = new NativeScreenProgram(TEMPORAL_FSH,
                    new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, WRITE_ALL));
        }
        if (nativeUpscale == null) {
            nativeUpscale = new NativeScreenProgram(UPSCALE_FSH,
                    new ColorTargetState(new BlendFunction(
                            BlendFactor.ONE, BlendFactor.ONE,
                            BlendFactor.ZERO, BlendFactor.ONE)));
        }
    }

    private Framebuffer resolveNativeHistory(CameraSnapshot cam, GpuTextureView rawView, GpuTextureView sceneDepth) {
        var previous = usingA ? historyA : historyB;
        var current = usingA ? historyB : historyA;
        nativeTemporal.setTexture("Raw", rawView);
        nativeTemporal.setTexture("DepthSampler", sceneDepth);
        nativeTemporal.setTexture("History", historyValid ? previous.colorTextureView(0) : rawView);
        nativeTemporal.setMatrix("PrevViewProj", prevViewProj);
        nativeTemporal.setMatrix("InvViewProj", cam.invViewProj);
        nativeTemporal.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        nativeTemporal.setVector("EyeDelta", (float) (cam.eye.x - prevEyeX),
                (float) (cam.eye.y - prevEyeY), (float) (cam.eye.z - prevEyeZ));
        nativeTemporal.setFloat("Feedback", historyValid ? FEEDBACK : 0);
        nativeTemporal.draw(current.renderPassDescriptor());
        usingA = !usingA;
        historyValid = true;
        return current;
    }
    //?}

    private VolumetricPass() {}

    public void render() {
        if (IrisCompat.shadersActive()) return;
        var settings = LightSettings.defaults();
        boolean off = !settings.volumetric() || settings.volumetricStrength() <= 0 || settings.volumetricDensity() <= 0
                || LightRegistry.INSTANCE.all().stream().noneMatch(VolumetricPass::scatters);
        if (off) {
            historyValid = false;
            return;
        }
        renderInternal();
    }

    private static boolean scatters(Light light) {
        return light.isEnabled() && light.intensity() > 0 && light.godray() > 0
                && light.godrayDensity() > 0 && light.godraySteps() > 0;
    }

    public void renderAfterIris() {
        if (IrisCompat.shadersActive()) renderInternal();
    }

    private void renderInternal() {
        LightSettings ls = LightSettings.defaults();
        if (IrisCompat.shadersActive() && !IrisCompat.canComposite()) {
            historyValid = false;
            return;
        }
        if (!ls.volumetric() || ls.volumetricSteps() <= 0 || ls.volumetricStrength() <= 0
                || ls.volumetricDensity() <= 0 || LightRegistry.INSTANCE.isEmpty()) {
            historyValid = false;
            return;
        }
        CameraSnapshot cam = CameraSnapshot.current();
        if (cam == null) return;

        //? if >=26.2 {
        renderNative(ls, cam);
        //?} else {
        /*ensure(ls.volumetricScale());
        capture.blitDepthFromMain();

        DeferredLightingPass.INSTANCE.renderVolumetricRaw(raw);

        try {
            GlState.beginFullscreen();

            Framebuffer resolved = raw;
            if (ls.volumetricTemporal()) {
                Framebuffer hPrev = usingA ? historyA : historyB;
                Framebuffer hCurr = usingA ? historyB : historyA;
                hCurr.begin();
                GlState.bindTexture(0, raw.colorTextureGlId(0));
                GlState.bindTexture(1, capture.depthTextureGlId());
                GlState.bindTexture(2, (historyValid ? hPrev : raw).colorTextureGlId(0));
                temporal.begin();
                temporal.setSampler("Raw", 0);
                temporal.setSampler("DepthSampler", 1);
                temporal.setSampler("History", 2);
                temporal.setMatrix4("PrevViewProj", prevViewProj);
                temporal.setMatrix4("InvViewProj", cam.invViewProj);
                temporal.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
                temporal.setVec3("EyeDelta",
                        (float) (cam.eye.x - prevEyeX), (float) (cam.eye.y - prevEyeY), (float) (cam.eye.z - prevEyeZ));
                temporal.setFloat("Feedback", historyValid ? FEEDBACK : 0f);
                temporal.draw();
                hCurr.end();
                resolved = hCurr;
                usingA = !usingA;
                historyValid = true;
            } else {
                historyValid = false;
            }

            int prevFbo = MainTargetFramebuffer.bind();
            try {
                GlState.bindTexture(0, resolved.colorTextureGlId(0));
                GlState.bindTexture(1, capture.depthTextureGlId());
                GlStateManager._enableBlend(0);
                GL11.glEnable(GL11.GL_BLEND);
                GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
                GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
                upscale.begin();
                upscale.setSampler("Scatter", 0);
                upscale.setSampler("DepthSampler", 1);
                upscale.draw();
            } finally {
                MainTargetFramebuffer.restore(prevFbo);
            }
        } finally {
            GlState.endFullscreen();
        }

        prevViewProj.set(cam.viewProj);
        prevEyeX = cam.eye.x;
        prevEyeY = cam.eye.y;
        prevEyeZ = cam.eye.z;
        *///?}
    }

    private void ensure(float s) {
        if (!initialized) {
            //? if <26.2 {
            /*temporal = new ShaderProgram(VSH, TEMPORAL_FSH);
            upscale = new ShaderProgram(VSH, UPSCALE_FSH);
            *///? }
            capture = Framebuffers.screen("Volumetric Capture", FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build());
            initialized = true;
        }
        if (raw != null && scale == s) return;
        if (raw != null) raw.dispose();
        if (historyA != null) historyA.dispose();
        if (historyB != null) historyB.dispose();
        raw = Framebuffers.screen("Volumetric Raw", s, FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
        historyA = Framebuffers.screen("Volumetric History A", s, FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
        historyB = Framebuffers.screen("Volumetric History B", s, FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
        scale = s;
        historyValid = false;
    }

    public void dispose() {
        //? if >=26.2 {
        if (nativeTemporal != null) {
            nativeTemporal.close();
            nativeTemporal = null;
        }
        if (nativeUpscale != null) {
            nativeUpscale.close();
            nativeUpscale = null;
        }
        nativeWidth = 0;
        nativeHeight = 0;
        //?}
        if (temporal != null) {
            temporal.close();
            temporal = null;
        }
        if (upscale != null) {
            upscale.close();
            upscale = null;
        }
        if (capture != null) {
            capture.dispose();
            capture = null;
        }
        if (raw != null) {
            raw.dispose();
            raw = null;
        }
        if (historyA != null) {
            historyA.dispose();
            historyA = null;
        }
        if (historyB != null) {
            historyB.dispose();
            historyB = null;
        }
        scale = -1f;
        initialized = false;
        historyValid = false;
    }
}
