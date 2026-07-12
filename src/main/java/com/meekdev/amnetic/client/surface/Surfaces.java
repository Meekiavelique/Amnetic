package com.meekdev.amnetic.client.surface;

import com.meekdev.amnetic.client.surface.internal.SurfaceRenderer;

// entry point for surface canvases, screen and world mounts arrive in later phases
public final class Surfaces {

    private Surfaces() {}

    public static HudSurface hud() {
        HudSurface surface = new HudSurface();
        SurfaceRenderer.INSTANCE.add(surface);
        return surface;
    }
}
