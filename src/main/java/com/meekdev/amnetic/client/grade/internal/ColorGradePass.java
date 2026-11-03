package com.meekdev.amnetic.client.grade.internal;

import com.meekdev.amnetic.client.compat.IrisCompat;
import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.grade.ColorGradeSettings;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ImportedTextures;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.util.HashSet;
import java.util.Set;

public final class ColorGradePass extends ScreenPass {

    public static final ColorGradePass INSTANCE = new ColorGradePass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/grade/colorgrade.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/grade/colorgrade.fsh");

    private Framebuffer capture;
    private final Set<Identifier> loadedLuts = new HashSet<>();
    private ColorGradeSettings settings;
    //? if >=26.2 {
    private NativeColorGrade nativePass;
    //?}

    private ColorGradePass() { super("ColorGrade"); }

    public void render(ColorGradeSettings s) {
        this.settings = s;
        //? if >=26.2 {
        if (!s.isEnabled() || s.isIdentity() || CameraSnapshot.current() == null) return;
        if (IrisCompat.shadersActive()) return;
        if (nativePass == null) nativePass = new NativeColorGrade();
        nativePass.render(s);
        //?} else {
        /*dispatch();
        *///?}
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
        //? if >=26.2 {
        if (nativePass != null) { nativePass.close(); nativePass = null; }
        //?}
        if (capture != null) { capture.dispose(); capture = null; }
    }

    private int loadTextureGlId(Identifier id) {
        var tm = Minecraft.getInstance().getTextureManager();
        if (!ImportedTextures.isImported(id) && loadedLuts.add(id)) {
            VanillaCompat.loadTexture(tm, id);
        }
        return VanillaCompat.glId(tm.getTexture(id));
    }
}
