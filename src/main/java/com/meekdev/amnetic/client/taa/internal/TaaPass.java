package com.meekdev.amnetic.client.taa.internal;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.gbuffer.GBuffer;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.taa.TaaSettings;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import java.util.Optional;
//? if >=26.2 {
import com.meekdev.amnetic.client.render.NativeScreenProgram;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import net.minecraft.client.Minecraft;
//?}

public final class TaaPass extends ScreenPass {
    public static final TaaPass INSTANCE = new TaaPass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/fullscreen.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/taa/taa.fsh");
    private static final Identifier BLIT_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/taa/blit.fsh");

    private static final double MAX_EYE_JUMP = 16.0;

    private ShaderProgram blit;
    private Framebuffer capture;
    private Framebuffer historyA, historyB;
    private boolean usingA;
    private TaaSettings settings;

    private final Matrix4f curViewProj = new Matrix4f();
    private final Matrix4f curInvViewProj = new Matrix4f();
    private final Matrix4f prevViewProj = new Matrix4f();
    private double prevEyeX, prevEyeY, prevEyeZ;
    private boolean historyValid;

    //? if >=26.2 {
    private NativeScreenProgram nativeResolve, nativeBlit;
    private int nativeWidth, nativeHeight;
    private void renderNative(CameraSnapshot cam) {
        if (capture == null) {
            capture = Framebuffers.screen("TAA Capture", FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build());
            historyA = Framebuffers.screen("TAA History A", FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
            historyB = Framebuffers.screen("TAA History B", FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
        }
        capture.blitColorFromMain(); capture.blitDepthFromMain();
        if (nativeWidth != capture.width() || nativeHeight != capture.height()) historyValid = false;
        nativeWidth = capture.width(); nativeHeight = capture.height();
        curViewProj.set(cam.viewProj);
        curInvViewProj.set(cam.invViewProj);
        double dx=cam.eye.x-prevEyeX,dy=cam.eye.y-prevEyeY,dz=cam.eye.z-prevEyeZ;
        var previous=usingA?historyA:historyB; var current=usingA?historyB:historyA;
        if(nativeResolve==null) nativeResolve=new NativeScreenProgram(Identifier.fromNamespaceAndPath("amnetic", "shaders/taa/native.fsh"),
                new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,15));
        nativeResolve.setTexture("ColorSampler",capture.colorTextureView(0));
        nativeResolve.setTexture("History",historyValid?previous.colorTextureView(0):capture.colorTextureView(0));
        nativeResolve.setTexture("DepthSampler",capture.depthTextureView());
        nativeResolve.setMatrix("PrevViewProj",prevViewProj); nativeResolve.setMatrix("InvViewProj",curInvViewProj);
        nativeResolve.setVector("EyeDelta",(float)dx,(float)dy,(float)dz);
        nativeResolve.setInt("ZeroToOne",cam.zeroToOne?1:0);
        nativeResolve.setInt("ReversedDepth",1);
        nativeResolve.setFloat("Feedback",historyValid && dx*dx+dy*dy+dz*dz<=MAX_EYE_JUMP*MAX_EYE_JUMP?settings.feedback():0);
        nativeResolve.setFloat("ClipSigma",settings.clipTightness());
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        nativeResolve.draw(encoder, current.renderPassDescriptor(), pass -> pass.draw(3,1,0,0));
        if (nativeBlit == null) nativeBlit = new NativeScreenProgram(BLIT_FSH, ColorTargetState.DEFAULT);
        nativeBlit.setTexture("ColorSampler",current.colorTextureView(0));
        nativeBlit.draw(encoder, RenderPassDescriptor.builder(()->"Amnetic TAA output")
                .withColorAttachment(Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView()).build(), pass -> pass.draw(3,1,0,0));
        encoder.submit();
        TaaJitter.INSTANCE.markResolved(); usingA=!usingA; historyValid=true;
        prevViewProj.set(curViewProj); prevEyeX=cam.eye.x;prevEyeY=cam.eye.y;prevEyeZ=cam.eye.z;
    }
    //? }
    private TaaPass() { super("TAA"); }

    public void render(TaaSettings s) {
        this.settings = s;
        if (!s.isEnabled()) historyValid = false;
        //? if >=26.2 {
        dispatchNative(() -> renderNative(CameraSnapshot.current()));
        //? } else {
        /*dispatch();
        *///? }
    }

    @Override protected boolean enabled() { return settings != null && settings.isEnabled(); }

    @Override
    protected ShaderProgram createProgram() {
        blit = new ShaderProgram(VSH, BLIT_FSH);
        capture = Framebuffers.screen("TAA Capture",
                FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build());
        historyA = Framebuffers.screen("TAA History A",
                FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
        historyB = Framebuffers.screen("TAA History B",
                FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
        return new ShaderProgram(VSH, FSH);
    }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram resolve) {
        capture.blitColorFromMain();
        capture.blitDepthFromMain();

        curViewProj.set(cam.viewProj);
        curInvViewProj.set(cam.invViewProj);

        double dx = cam.eye.x - prevEyeX, dy = cam.eye.y - prevEyeY, dz = cam.eye.z - prevEyeZ;
        boolean cut = (dx * dx + dy * dy + dz * dz) > MAX_EYE_JUMP * MAX_EYE_JUMP;

        Framebuffer hPrev = usingA ? historyA : historyB;
        Framebuffer hCurr = usingA ? historyB : historyA;

        hCurr.begin();
        GlState.bindTexture(0, capture.colorTextureGlId(0));
        GlState.bindTexture(1, (historyValid && hPrev.isAllocated() ? hPrev : capture).colorTextureGlId(0));
        GlState.bindTexture(2, capture.depthTextureGlId());
        boolean moves = GBuffer.isPopulated();
        GlState.bindTexture(3, moves ? GBuffer.velocityTextureGlId() : 0);
        boolean reactive = ReactivePass.INSTANCE.ready();
        GlState.bindTexture(4, reactive ? ReactivePass.INSTANCE.maskGlId() : 0);
        resolve.begin();
        resolve.setSampler("Velocity", 3);
        resolve.setSampler("Reactive", 4);
        resolve.setInt("HasVelocity", moves ? 1 : 0);
        resolve.setInt("HasReactive", reactive ? 1 : 0);
        resolve.setSampler("ColorSampler", 0);
        resolve.setSampler("History", 1);
        resolve.setSampler("DepthSampler", 2);
        resolve.setMatrix4("PrevViewProj", prevViewProj);
        resolve.setMatrix4("InvViewProj", curInvViewProj);
        resolve.setVec3("EyeDelta", (float) dx, (float) dy, (float) dz);
        resolve.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        resolve.setFloat("Feedback", historyValid && !cut ? settings.feedback() : 0f);
        resolve.setFloat("ClipSigma", settings.clipTightness());
        resolve.draw();
        hCurr.end();

        int prevFbo = MainTargetFramebuffer.bind();
        try {
            GlStateManager._disableBlend(0);
            GlState.bindTexture(0, hCurr.colorTextureGlId(0));
            blit.begin();
            blit.setSampler("ColorSampler", 0);
            blit.draw();
        } finally {
            MainTargetFramebuffer.restore(prevFbo);
        }
        TaaJitter.INSTANCE.markResolved();

        usingA = !usingA;
        historyValid = true;
        prevViewProj.set(curViewProj);
        prevEyeX = cam.eye.x; prevEyeY = cam.eye.y; prevEyeZ = cam.eye.z;
        return true;
    }

    @Override
    protected void onDispose() {
        //? if >=26.2 {
        if(nativeResolve!=null){nativeResolve.close();nativeResolve=null;}
        if(nativeBlit!=null){nativeBlit.close();nativeBlit=null;}
        //? }
        if (blit != null) { blit.close(); blit = null; }
        if (capture != null) { capture.dispose(); capture = null; }
        if (historyA != null) { historyA.dispose(); historyA = null; }
        if (historyB != null) { historyB.dispose(); historyB = null; }
        historyValid = false;
    }
}
