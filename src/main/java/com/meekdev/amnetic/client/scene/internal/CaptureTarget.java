package com.meekdev.amnetic.client.scene.internal;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class CaptureTarget {

    private TextureTarget target;
    private int width = -1;
    private int height = -1;
    private CaptureColorTexture registered;

    public RenderTarget ensure(float scale) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int w = Math.max(1, Math.round(main.width * scale));
        int h = Math.max(1, Math.round(main.height * scale));
        if (target == null) {
            target = new TextureTarget("amnetic_capture", w, h, true);
            width = w;
            height = h;
        } else if (w != width || h != height) {
            target.resize(w, h);
            width = w;
            height = h;
        }
        return target;
    }

    public RenderTarget target() {
        return target;
    }

    public int colorTextureGlId() {
        if (target == null) return 0;
        GpuTexture color = target.getColorTexture();
        return color instanceof GlTexture gl ? gl.glId() : 0;
    }

    public int depthTextureGlId() {
        if (target == null) return 0;
        GpuTexture depth = target.getDepthTexture();
        return depth instanceof GlTexture gl ? gl.glId() : 0;
    }

    public void registerColor(Identifier id) {
        if (target == null) return;
        if (registered == null) registered = new CaptureColorTexture();
        registered.update(target);
        if (!registered.isRegistered()) {
            Minecraft.getInstance().getTextureManager().register(id, registered);
            registered.markRegistered();
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
