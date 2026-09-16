package com.meekdev.amnetic.client.particle;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
//? if <1.21.5 {
/*import net.minecraft.server.packs.resources.ResourceManager;
*///?}

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
        if (main == null || VanillaCompat.depthTextureGlId(main) == 0) {
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
        return VanillaCompat.depthTextureGlId(instance.snapshot);
    }

    private boolean capture(RenderTarget src) {
        if (snapshot == null) {
            snapshot = VanillaCompat.textureTarget("amnetic_scene_depth", src.width, src.height, true);
        } else if (snapshot.width != src.width || snapshot.height != src.height) {
            VanillaCompat.resize(snapshot, src.width, src.height);
            directCopyProven = false;
        }
        if (VanillaCompat.depthTextureGlId(snapshot) == 0) {
            return false;
        }
        if (!fastCopyDepth(src)) {
            snapshot.copyDepthFrom(src);
        }
        //? if >=1.21.5 {
        this.texture = snapshot.getDepthTexture();
        this.textureView = snapshot.getDepthTextureView();
        //?} else {
        /*this.id = snapshot.getDepthTextureId();
        *///?}
        return true;
    }

    private boolean fastCopyDepth(RenderTarget src) {
        if (!directCopy) {
            return false;
        }
        int from = VanillaCompat.depthTextureGlId(src);
        int to = VanillaCompat.depthTextureGlId(snapshot);
        if (from == 0 || to == 0 || !GL.getCapabilities().OpenGL43) {
            directCopy = false;
            return false;
        }
        if (!directCopyProven) {
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {
                continue;
            }
        }
        GL43.glCopyImageSubData(
                from, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                to, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
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

    //? if <1.21.5 {
    /*@Override
    public void load(ResourceManager resourceManager) {
    }
    *///?}
}
