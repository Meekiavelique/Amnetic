package com.meekdev.amnetic.client.surface;

import com.meekdev.amnetic.client.surface.internal.SurfaceRenderer;
import com.meekdev.amnetic.client.surface.internal.WorldSurfaceRenderer;
import net.minecraft.resources.Identifier;

// entry point for surface canvases, world mounts arrive in a later phase
public final class Surfaces {

    private static Identifier defaultFont;

    private Surfaces() {}

    // font used by text widgets that don't set their own
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

    // modal screen: captures mouse and keyboard until closed, open() to show
    public static ScreenSurface screen(String name) {
        ScreenSurface surface = new ScreenSurface(name);
        SurfaceRenderer.INSTANCE.add(surface);
        return surface;
    }

    // panel living in the world, meters wide/tall, crosshair-pickable within range
    public static WorldSurface world(float widthMeters, float heightMeters) {
        WorldSurface surface = new WorldSurface(widthMeters, heightMeters);
        WorldSurfaceRenderer.INSTANCE.add(surface);
        return surface;
    }
}
