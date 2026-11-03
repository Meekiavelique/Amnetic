package com.meekdev.amnetic.client.taa.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.taa.Taa;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class ReactivePass extends ScreenPass {

    public static final ReactivePass INSTANCE = new ReactivePass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/fullscreen.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/taa/reactive.fsh");

    private Framebuffer before;
    private Framebuffer mask;
    private boolean copied;
    private boolean fresh;
    private boolean ready;

    private ReactivePass() { super("Reactive"); }

    @Override protected boolean enabled() { return Taa.settings().isEnabled(); }

    @Override
    protected ShaderProgram createProgram() {
        before = Framebuffers.screen("TAA Reactive Before", FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
        mask = Framebuffers.screen("TAA Reactive", FramebufferSpec.builder().color(ColorFormat.R8).build());
        return new ShaderProgram(VSH, FSH);
    }

    private boolean copying;

    public void before(boolean frameStart) {
        if (frameStart) ready = false;
        copying = true;
        fresh = frameStart;
        dispatch();
    }

    public void after() {
        copying = false;
        dispatch();
    }

    public boolean ready() { return ready && Taa.settings().isEnabled(); }

    public int maskGlId() { return mask == null ? 0 : mask.colorTextureGlId(0); }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram program) {
        if (copying) {
            before.blitColorFromMain();
            copied = true;
            if (fresh) {
                mask.begin();
                mask.clear(0f, 0f, 0f, 0f);
                mask.end();
            }
            return true;
        }
        if (!copied) return false;
        copied = false;
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main == null) return false;
        mask.begin();
        GlState.bindTexture(0, before.colorTextureGlId(0));
        GlState.bindTexture(1, VanillaCompat.colorTextureGlId(main));
        GlStateManager._enableBlend(0);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendEquation(GL14.GL_MAX);
        program.begin();
        program.setSampler("Before", 0);
        program.setSampler("After", 1);
        program.draw();
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GlStateManager._disableBlend(0);
        GL11.glDisable(GL11.GL_BLEND);
        mask.end();
        ready = true;
        return true;
    }

    @Override
    protected void onDispose() {
        if (before != null) { before.dispose(); before = null; }
        if (mask != null) { mask.dispose(); mask = null; }
        ready = copied = false;
    }
}
