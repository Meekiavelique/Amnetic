package com.meekdev.amnetic.client.subsurface;

public final class SubsurfaceSettings {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }

    public SubsurfaceSettings enabled(boolean e) {
        enabled = e;
        return this;
    }
}
