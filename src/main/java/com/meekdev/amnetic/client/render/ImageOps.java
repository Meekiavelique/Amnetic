package com.meekdev.amnetic.client.render;

import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import net.minecraft.resources.Identifier;

public final class ImageOps {

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/fullscreen.vsh");
    private static final Identifier DILATE = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/dilate.fsh");
    private static final Identifier EDGE = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/edge.fsh");

    private static ShaderProgram dilate;
    private static ShaderProgram edge;

    private ImageOps() {
    }

    public static void dilate(int sourceGlId, int sourceWidth, int sourceHeight, Framebuffer dest, int radius) {
        if (dilate == null) {
            dilate = new ShaderProgram(VSH, DILATE);
        }
        dest.begin();
        GlState.beginFullscreen();
        dilate.begin();
        GlState.bindTexture(0, sourceGlId);
        dilate.setSampler("Source", 0);
        dilate.setVec2("Texel", 1f / sourceWidth, 1f / sourceHeight);
        dilate.setInt("Radius", radius);
        dilate.draw();
        GlState.endFullscreen();
        dest.end();
    }

    public static void edge(int sourceGlId, int sourceWidth, int sourceHeight, Framebuffer dest, float thickness) {
        if (edge == null) {
            edge = new ShaderProgram(VSH, EDGE);
        }
        dest.begin();
        GlState.beginFullscreen();
        edge.begin();
        GlState.bindTexture(0, sourceGlId);
        edge.setSampler("Source", 0);
        edge.setVec2("Texel", 1f / sourceWidth, 1f / sourceHeight);
        edge.setFloat("Thickness", thickness);
        edge.draw();
        GlState.endFullscreen();
        dest.end();
    }

    public static void dispose() {
        if (dilate != null) {
            dilate.close();
            dilate = null;
        }
        if (edge != null) {
            edge.close();
            edge = null;
        }
    }
}
