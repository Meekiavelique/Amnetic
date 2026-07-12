package com.meekdev.amnetic.client.taa.internal;

import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.taa.TaaSettings;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.resources.Identifier;

public final class CasPass extends ScreenPass {

    public static final CasPass INSTANCE = new CasPass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/fullscreen.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/taa/cas.fsh");

    private Framebuffer capture;
    private TaaSettings settings;

    private CasPass() { super("CAS"); }

    public void render(TaaSettings s) {
        this.settings = s;
        dispatch();
    }

    @Override
    protected boolean enabled() {
        return settings != null && settings.isEnabled() && settings.sharpness() > 0f;
    }

    @Override
    protected ShaderProgram createProgram() {
        capture = Framebuffers.captureColor();
        return new ShaderProgram(VSH, FSH);
    }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram program) {
        capture.blitColorFromMain();

        int prevFbo = MainTargetFramebuffer.bind();
        try {
            GlStateManager._disableBlend();
            GlState.bindTexture(0, capture.colorTextureGlId(0));
            program.begin();
            program.setSampler("ColorSampler", 0);
            program.setFloat("Sharpness", settings.sharpness());
            program.draw();
        } finally {
            MainTargetFramebuffer.restore(prevFbo);
        }
        return true;
    }

    @Override
    protected void onDispose() {
        if (capture != null) { capture.dispose(); capture = null; }
    }
}
