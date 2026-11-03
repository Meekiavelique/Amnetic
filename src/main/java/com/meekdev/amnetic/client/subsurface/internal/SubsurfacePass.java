package com.meekdev.amnetic.client.subsurface.internal;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.material.internal.MaterialParams;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.render.TexelBuffer;
import com.meekdev.amnetic.client.subsurface.SubsurfaceSettings;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import java.util.Optional;
//? if >=26.2 {
import com.meekdev.amnetic.client.render.NativeScreenProgram;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
//?}

public final class SubsurfacePass extends ScreenPass {

    public static final SubsurfacePass INSTANCE = new SubsurfacePass();

    private static final Identifier VSH =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/ssao/ssao.vsh");
    private static final Identifier BLUR_FSH =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/subsurface/blur.fsh");
    private static final Identifier COMPOSITE_FSH =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/subsurface/composite.fsh");

    private ShaderProgram composite;
    private Framebuffer capture;
    private Framebuffer horizontal;
    private Framebuffer vertical;
    private SubsurfaceSettings settings;

    //? if >=26.2 {
    private static final int WRITE_ALL = 15;
    private NativeScreenProgram nativeBlur;
    private NativeScreenProgram nativeComposite;

    private void renderNative(CameraSnapshot cam) {
        ensureBuffers();
        capture.blitColorFromMain();
        capture.blitDepthFromMain();
        if (nativeBlur == null) {
            nativeBlur = new NativeScreenProgram(BLUR_FSH,
                    new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, WRITE_ALL));
        }
        if (nativeComposite == null) nativeComposite = new NativeScreenProgram(COMPOSITE_FSH, ColorTargetState.DEFAULT);
        bindBlur(cam);
        nativeBlur.setTexture("ColorSampler", capture.colorTextureView(0));
        nativeBlur.setVector("Direction", 1, 0);
        nativeBlur.draw(horizontal.renderPassDescriptor());
        nativeBlur.setTexture("ColorSampler", horizontal.colorTextureView(0));
        nativeBlur.setVector("Direction", 0, 1);
        nativeBlur.draw(vertical.renderPassDescriptor());
        nativeComposite.setTexture("OriginalSampler", capture.colorTextureView(0));
        nativeComposite.setTexture("DiffusedSampler", vertical.colorTextureView(0));
        nativeComposite.setTexture("GMaterialSampler", GBufferTargets.INSTANCE.nativeColorView(1));
        nativeComposite.setTexels("MaterialTexels", MaterialParams.INSTANCE.nativeSlice());
        var mainColor = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
        nativeComposite.draw(RenderPassDescriptor.builder(() -> "Amnetic subsurface composite").withColorAttachment(mainColor).build());
    }

    private void bindBlur(CameraSnapshot cam) {
        nativeBlur.setTexture("DepthSampler", capture.depthTextureView());
        nativeBlur.setTexture("GMaterialSampler", GBufferTargets.INSTANCE.nativeColorView(1));
        nativeBlur.setTexels("MaterialTexels", MaterialParams.INSTANCE.nativeSlice());
        nativeBlur.setMatrix("InvViewProj", cam.invViewProj);
        nativeBlur.setMatrix("View", cam.view);
        nativeBlur.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        nativeBlur.setVector("ScreenSize", capture.width(), capture.height());
        nativeBlur.setFloat("ProjScale", .5f * cam.projection.m11() * capture.height());
    }
    //? }

    private SubsurfacePass() {
        super("Subsurface");
    }

    public void render(SubsurfaceSettings s) {
        this.settings = s;
        //? if >=26.2 {
        dispatchNative(() -> renderNative(CameraSnapshot.current()));
        //? } else {
        /*dispatch();
        *///? }
    }

    @Override
    protected boolean enabled() {
        return settings != null && settings.isEnabled()
                && MaterialParams.INSTANCE.hasSubsurfaceMaterial()
                && GBufferTargets.INSTANCE.isPopulated();
    }

    @Override
    protected ShaderProgram createProgram() {
        composite = new ShaderProgram(VSH, COMPOSITE_FSH);
        return new ShaderProgram(VSH, BLUR_FSH);
    }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram blur) {
        ensureBuffers();

        capture.blitColorFromMain();
        capture.blitDepthFromMain();

        float height = Minecraft.getInstance().getWindow().getHeight();
        float projScale = 0.5f * cam.projection.m11() * height;
        float w = capture.width();
        float h = capture.height();

        MaterialParams.INSTANCE.bind();

        horizontal.begin();
        GlState.bindTexture(0, capture.colorTextureGlId(0));
        GlState.bindTexture(1, capture.depthTextureGlId());
        GlState.bindTexture(2, GBufferTargets.INSTANCE.materialGlId());
        blur.begin();
        blur.setSampler("ColorSampler", 0);
        blur.setSampler("DepthSampler", 1);
        blur.setSampler("GMaterialSampler", 2);
        blur.setSampler("MaterialTexels", MaterialParams.UNIT);
        blur.setMatrix4("InvViewProj", cam.invViewProj);
        blur.setMatrix4("View", cam.view);
        blur.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        blur.setVec2("Direction", 1f, 0f);
        blur.setVec2("ScreenSize", w, h);
        blur.setFloat("ProjScale", projScale);
        blur.draw();
        horizontal.end();

        vertical.begin();
        GlState.bindTexture(0, horizontal.colorTextureGlId(0));
        GlState.bindTexture(1, capture.depthTextureGlId());
        GlState.bindTexture(2, GBufferTargets.INSTANCE.materialGlId());
        blur.begin();
        blur.setSampler("ColorSampler", 0);
        blur.setSampler("DepthSampler", 1);
        blur.setSampler("GMaterialSampler", 2);
        blur.setSampler("MaterialTexels", MaterialParams.UNIT);
        blur.setMatrix4("InvViewProj", cam.invViewProj);
        blur.setMatrix4("View", cam.view);
        blur.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        blur.setVec2("Direction", 0f, 1f);
        blur.setVec2("ScreenSize", w, h);
        blur.setFloat("ProjScale", projScale);
        blur.draw();
        vertical.end();

        int prevFbo = MainTargetFramebuffer.bind();
        try {
            GlStateManager._disableBlend(0);
            GL11.glDisable(GL11.GL_BLEND);
            GlState.bindTexture(0, capture.colorTextureGlId(0));
            GlState.bindTexture(1, vertical.colorTextureGlId(0));
            GlState.bindTexture(2, GBufferTargets.INSTANCE.materialGlId());
            composite.begin();
            composite.setSampler("OriginalSampler", 0);
            composite.setSampler("DiffusedSampler", 1);
            composite.setSampler("GMaterialSampler", 2);
            composite.setSampler("MaterialTexels", MaterialParams.UNIT);
            composite.draw();
        } finally {
            TexelBuffer.unbind(MaterialParams.UNIT);
            MainTargetFramebuffer.restore(prevFbo);
        }
        return true;
    }

    @Override
    protected void onDispose() {
        //? if >=26.2 {
        if (nativeBlur != null) nativeBlur.close();
        nativeBlur = null;
        if (nativeComposite != null) nativeComposite.close();
        nativeComposite = null;
        //? }
        if (composite != null) composite.close();
        composite = null;
        if (capture != null) capture.dispose();
        capture = null;
        if (horizontal != null) horizontal.dispose();
        horizontal = null;
        if (vertical != null) vertical.dispose();
        vertical = null;
    }

    private void ensureBuffers() {
        if (capture == null) {
            capture = Framebuffers.screen("SSS Capture", FramebufferSpec.builder()
                    .color(ColorFormat.RGBA16F).depthTexture().build());
            horizontal = Framebuffers.screen("SSS Horizontal", FramebufferSpec.builder()
                    .color(ColorFormat.RGBA16F).build());
            vertical = Framebuffers.screen("SSS Vertical", FramebufferSpec.builder()
                    .color(ColorFormat.RGBA16F).build());
        }
    }
}
