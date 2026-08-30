package com.meekdev.amnetic.client.surface;

import com.meekdev.amnetic.client.surface.internal.SurfaceRenderer;
import com.meekdev.amnetic.client.surface.internal.WorldSurfaceRenderer;
import net.minecraft.resources.Identifier;

public final class Surfaces {

    private static Identifier defaultFont;

    private Surfaces() {}

    public static void defaultFont(Identifier fontId) {
        defaultFont = fontId;
    }

    public static Identifier defaultFont() {
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
