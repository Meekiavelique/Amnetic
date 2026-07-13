package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import java.util.function.IntSupplier;

// textured quad from a raw gl texture id, the supplier form makes it a live view onto
// anything that renders to a texture (scene captures, offscreen model views, portals)
public class Image extends Widget {

    final IntSupplier texture;
    int tint = 0xFFFFFFFF;
    boolean flipV;

    public Image(int glTextureId) {
        this(() -> glTextureId);
    }

    public Image(IntSupplier glTextureId) {
        this.texture = glTextureId;
    }

    public Image tint(int argb) { tint = argb; return this; }

    // framebuffer textures are stored bottom-up, flip when showing a capture
    public Image flipV(boolean flip) { flipV = flip; return this; }

    @Override
    protected float contentWidth() { return 64; }

    @Override
    protected float contentHeight(float forWidth) { return 64; }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        int id = texture.getAsInt();
        if (id == 0) return;
        d.image(id, x, flipV ? y + h : y, w, flipV ? -h : h, fade(tint, alpha));
    }

    // fluent overrides so chains keep the subtype
    @Override public Image size(float w, float h) { super.size(w, h); return this; }
    @Override public Image width(float w) { super.width(w); return this; }
    @Override public Image height(float h) { super.height(h); return this; }
    @Override public Image grow(float g) { super.grow(g); return this; }
    @Override public Image anchor(Anchor a) { super.anchor(a); return this; }
    @Override public Image offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public Image padding(float p) { super.padding(p); return this; }
    @Override public Image visible(boolean v) { super.visible(v); return this; }
    @Override public Image opacity(float o) { super.opacity(o); return this; }
}
