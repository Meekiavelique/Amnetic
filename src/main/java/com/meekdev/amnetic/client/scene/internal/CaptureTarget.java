package com.meekdev.amnetic.client.scene.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
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
            target = VanillaCompat.textureTarget("amnetic_capture", w, h, true);
            width = w;
            height = h;
        } else if (w != width || h != height) {
            VanillaCompat.resize(target, w, h);
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
        return VanillaCompat.colorTextureGlId(target);
    }

    public int depthTextureGlId() {
        if (target == null) return 0;
        return VanillaCompat.depthTextureGlId(target);
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
