package com.meekdev.amnetic.client.particle;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;

public final class SceneDepth extends AbstractTexture {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("amnetic", "scene_depth");

    private static SceneDepth instance;
    private TextureTarget snapshot;
    private boolean registered;
    private boolean directCopy = true;
    private boolean directCopyProven;

    private SceneDepth() {}

    public static boolean update() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null || !main.useDepth || main.getDepthTexture() == null) {
            return false;
        }
        if (instance == null) {
            instance = new SceneDepth();
        }
        if (!instance.capture(main)) {
            return false;
        }
        if (!instance.registered) {
            mc.getTextureManager().register(ID, instance);
            instance.registered = true;
        }
        return true;
    }

    public static int snapshotDepthGlId() {
        if (instance == null || instance.snapshot == null) {
            return 0;
        }
        GpuTexture depth = instance.snapshot.getDepthTexture();
        return depth instanceof GlTexture gl ? gl.glId() : 0;
    }

    private boolean capture(RenderTarget src) {
        if (snapshot == null) {
            snapshot = new TextureTarget("amnetic_scene_depth", src.width, src.height, true);
        } else if (snapshot.width != src.width || snapshot.height != src.height) {
            snapshot.resize(src.width, src.height);
            directCopyProven = false;
        }
        if (snapshot.getDepthTexture() == null) {
            return false;
        }
        if (!fastCopyDepth(src)) {
            snapshot.copyDepthFrom(src);
        }
        this.texture = snapshot.getDepthTexture();
        this.textureView = snapshot.getDepthTextureView();
        return true;
    }

    private boolean fastCopyDepth(RenderTarget src) {
        if (!directCopy) {
            return false;
        }
        if (!(src.getDepthTexture() instanceof GlTexture from)
                || !(snapshot.getDepthTexture() instanceof GlTexture to)
                || !GL.getCapabilities().OpenGL43) {
            directCopy = false;
            return false;
        }
        if (!directCopyProven) {
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {
                continue;
            }
        }
        GL43.glCopyImageSubData(
                from.glId(), GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                to.glId(), GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                src.width, src.height, 1);
        if (!directCopyProven) {
            directCopyProven = true;
            if (GL11.glGetError() != GL11.GL_NO_ERROR) {
                directCopy = false;
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
    }
}
