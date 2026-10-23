package com.meekdev.amnetic.client.surface;

import com.meekdev.amnetic.client.surface.internal.SurfaceRenderer;
import com.meekdev.amnetic.client.surface.internal.WorldSurfaceRenderer;
import net.minecraft.resources.ResourceLocation;

public final class Surfaces {

    private static ResourceLocation defaultFont;

    private Surfaces() {}

    public static void defaultFont(ResourceLocation fontId) {
        defaultFont = fontId;
    }

    public static ResourceLocation defaultFont() {
        return defaultFont;
    }

    public static HudSurface hud() {
        HudSurface surface = new HudSurface();
        SurfaceRenderer.INSTANCE.add(surface);
        return surface;
    }

    public static ScreenSurface screen(String name) {
        ScreenSurface surface = new ScreenSurface(name);
        SurfaceRenderer.INSTANCE.add(surface);
        return surface;
    }

    public static WorldSurface world(float widthMeters, float heightMeters) {
        WorldSurface surface = new WorldSurface(widthMeters, heightMeters);
        WorldSurfaceRenderer.INSTANCE.add(surface);
        return surface;
    }
}
