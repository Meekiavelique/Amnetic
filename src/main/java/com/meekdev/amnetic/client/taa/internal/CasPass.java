package com.meekdev.amnetic.client.taa.internal;

import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.taa.TaaSettings;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.minecraft.resources.Identifier;

public final class CasPass extends ScreenPass {

    public static final CasPass INSTANCE = new CasPass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/fullscreen.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/taa/cas.fsh");

    private Framebuffer capture;
    private TaaSettings settings;
    //? if >=26.2 {
    private com.meekdev.amnetic.client.render.NativeScreenProgram nativeProgram;
    //? }

    private CasPass() { super("CAS"); }

    public void render(TaaSettings s) {
        this.settings = s;
        //? if >=26.2 {
        dispatchNative(() -> {
            if(capture==null)capture=Framebuffers.captureColor();capture.blitColorFromMain();
            if(nativeProgram==null)nativeProgram=new com.meekdev.amnetic.client.render.NativeScreenProgram(FSH,com.mojang.renderpearl.api.pipeline.ColorTargetState.DEFAULT);
            nativeProgram.setTexture("ColorSampler",capture.colorTextureView(0));nativeProgram.setFloat("Sharpness",settings.sharpness());
            nativeProgram.draw(com.mojang.renderpearl.api.commands.RenderPassDescriptor.builder(()->"Amnetic CAS")
                    .withColorAttachment(net.minecraft.client.Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView()).build());
        });
        //? } else {
        /*dispatch();
        *///? }
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
            GlStateManager._disableBlend(0);
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
        //? if >=26.2 {
        if(nativeProgram!=null){nativeProgram.close();nativeProgram=null;}
        //? }
        if (capture != null) { capture.dispose(); capture = null; }
    }
}
