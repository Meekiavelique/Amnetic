package com.meekdev.amnetic.client.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
//? if >=1.21.5 {
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
//?} else {
/*import com.meekdev.amnetic.mixin.accessor.LightTextureAccessor;
*///?}
//? if >=26.1 {
import com.mojang.blaze3d.systems.RenderSystem;
//?} else {
/*import net.minecraft.client.renderer.GameRenderer;
*///?}

public final class VanillaCompat {

    //? if <26.1 {
    /*private static float worldFov = 70f;
    private static Frustum cullFrustum;

    public static void recordWorldFov(float fov) { worldFov = fov; }
    public static void recordCullFrustum(Frustum frustum) { cullFrustum = frustum; }
    *///?}

    private VanillaCompat() {}

    // whether clip-space depth runs 0..1 rather than GL's -1..1
    public static boolean zeroToOne() {
        //? if >=26.1 {
        return RenderSystem.getDevice().isZZeroToOne();
        //?} else {
        /*return false;
        *///?}
    }

    public static float nearPlane() {
        //? if >=26.1 {
        return Camera.PROJECTION_Z_NEAR;
        //?} else {
        /*return GameRenderer.PROJECTION_Z_NEAR;
        *///?}
    }

    public static float fov(Camera camera) {
        //? if >=26.1 {
        return camera.getFov();
        //?} else {
        /*return worldFov;
        *///?}
    }

    public static Frustum cullFrustum(Camera camera) {
        //? if >=26.1 {
        return camera.getCullFrustum();
        //?} else {
        /*return cullFrustum;
        *///?}
    }

    public static int levelLightmapGlId() {
        var renderer = Minecraft.getInstance().gameRenderer;
        if (renderer == null) return 0;
        //? if >=26.1 {
        var view = renderer.levelLightmap();
        return view != null ? glId(view.texture()) : 0;
        //?} else if >=1.21.5 {
        /*var view = renderer.lightTexture().getTextureView();
        return view != null ? glId(view.texture()) : 0;
        *///?} else {
        /*return ((LightTextureAccessor) renderer.lightTexture()).amnetic$getTexture().getId();
        *///?}
    }

    public static int glId(AbstractTexture texture) {
        if (texture == null) return 0;
        //? if >=1.21.5 {
        return glId(texture.getTexture());
        //?} else {
        /*return Math.max(texture.getId(), 0);
        *///?}
    }

    public static int colorTextureGlId(RenderTarget target) {
        //? if >=1.21.5 {
        return glId(target.getColorTexture());
        //?} else {
        /*return Math.max(target.getColorTextureId(), 0);
        *///?}
    }

    public static int depthTextureGlId(RenderTarget target) {
        if (!target.useDepth) return 0;
        //? if >=1.21.5 {
        return glId(target.getDepthTexture());
        //?} else {
        /*return Math.max(target.getDepthTextureId(), 0);
        *///?}
    }

    //? if >=1.21.5 {
    public static int glId(GpuTexture texture) {
        return texture instanceof GlTexture gl ? gl.glId() : 0;
    }
    //?}

    public static TextureTarget textureTarget(String name, int width, int height, boolean depth) {
        //? if >=1.21.5 {
        return new TextureTarget(name, width, height, depth);
        //?} else {
        /*return new TextureTarget(width, height, depth, Minecraft.ON_OSX);
        *///?}
    }

    public static void resize(RenderTarget target, int width, int height) {
        //? if >=1.21.5 {
        target.resize(width, height);
        //?} else {
        /*target.resize(width, height, Minecraft.ON_OSX);
        *///?}
    }

    public static RenderTarget levelTarget(Identifier id) {
        var lr = Minecraft.getInstance().levelRenderer;
        if (lr == null || !id.getNamespace().equals("minecraft")) return null;
        return switch (id.getPath()) {
            case "translucent" -> lr.getTranslucentTarget();
            case "item_entity" -> lr.getItemEntityTarget();
            case "particles" -> lr.getParticlesTarget();
            case "weather" -> lr.getWeatherTarget();
            case "clouds" -> lr.getCloudsTarget();
            //? if >=1.21.2 {
            case "entity_outline" -> lr.entityOutlineTarget();
            //?} else {
            /*case "entity_outline" -> lr.entityTarget();
            *///?}
            default -> null;
        };
    }

    public static long windowHandle() {
        //? if >=1.21.9 {
        return Minecraft.getInstance().getWindow().handle();
        //?} else {
        /*return Minecraft.getInstance().getWindow().getWindow();
        *///?}
    }

    public static void loadTexture(TextureManager textures, Identifier id) {
        //? if >=1.21.5 {
        textures.registerAndLoad(id, new SimpleTexture(id));
        //?} else {
        /*textures.register(id, new SimpleTexture(id));
        *///?}
    }

    public static long dayTime(ClientLevel level) {
        //? if >=26.1 {
        return level.getDefaultClockTime();
        //?} else {
        /*return level.getDayTime();
        *///?}
    }
}
