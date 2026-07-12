package com.meekdev.amnetic.client.grade.internal;

import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.grade.ColorGradeSettings;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ImportedTextures;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.blaze3d.opengl.GlTexture;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;

public final class ColorGradePass extends ScreenPass {

    public static final ColorGradePass INSTANCE = new ColorGradePass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/grade/colorgrade.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/grade/colorgrade.fsh");

    private Framebuffer capture;
    private final Set<Identifier> loadedLuts = new HashSet<>();
    private ColorGradeSettings settings;

    private ColorGradePass() { super("ColorGrade"); }

    public void render(ColorGradeSettings s) {
        this.settings = s;
        dispatch();
    }

    @Override protected boolean enabled() { return settings != null && settings.isEnabled(); }

    @Override protected ShaderProgram createProgram() {
        capture = Framebuffers.captureColor();
        return new ShaderProgram(VSH, FSH);
    }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram program) {
        ColorGradeSettings s = settings;
        capture.blitColorFromMain();

        int lutGl = (s.lut() != null) ? loadTextureGlId(s.lut()) : 0;
        boolean hasLut = lutGl != 0;

        int prevFbo = MainTargetFramebuffer.bind();
        try {
            GlState.bindTexture(0, capture.colorTextureGlId(0));
            if (hasLut) GlState.bindTexture(1, lutGl);

            program.begin();
            program.setSampler("ColorSampler", 0);
            program.setSampler("LutSampler", 1);
            program.setInt("HasLut", hasLut ? 1 : 0);
            program.setFloat("LutSize", s.lutSize());
            program.setFloat("LutIntensity", s.lutIntensity());
            program.setFloat("Exposure", s.exposure());
            program.setFloat("Contrast", s.contrast());
            program.setFloat("Saturation", s.saturation());
            program.setFloat("Brightness", s.brightness());
            program.setFloat("Temperature", s.temperature());
            program.setFloat("Tint", s.tint());
            program.setFloat("Gamma", s.gamma());
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

    private int loadTextureGlId(Identifier id) {
        var tm = Minecraft.getInstance().getTextureManager();
        if (!ImportedTextures.isImported(id) && loadedLuts.add(id)) {
            tm.registerAndLoad(id, new SimpleTexture(id));
        }
        AbstractTexture tex = tm.getTexture(id);
        if (tex != null && tex.getTexture() instanceof GlTexture gl) {
            return gl.glId();
        }
        return 0;
    }
}
