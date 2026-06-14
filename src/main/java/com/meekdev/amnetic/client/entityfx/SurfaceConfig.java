package com.meekdev.amnetic.client.entityfx;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;
import net.minecraft.resources.Identifier;

public final class SurfaceConfig {

    final Map<Integer, Float> constants = new LinkedHashMap<>();
    final Map<Integer, DoubleSupplier> suppliers = new LinkedHashMap<>();
    final Map<String, Identifier> samplers = new LinkedHashMap<>();
    boolean replaceBody = true;
    boolean skin = false;
    boolean sceneColor = true;
    boolean sceneDepth = false;

    SurfaceConfig() {}

    public SurfaceConfig skin(boolean enabled) {
        this.skin = enabled;
        return this;
    }

    public SurfaceConfig sceneColor(boolean enabled) {
        this.sceneColor = enabled;
        return this;
    }

    public SurfaceConfig sceneDepth(boolean enabled) {
        this.sceneDepth = enabled;
        return this;
    }

    public SurfaceConfig uniform(int channel, float value01) {
        constants.put(channel, value01);
        suppliers.remove(channel);
        return this;
    }

    public SurfaceConfig uniform(int channel, DoubleSupplier supplier) {
        suppliers.put(channel, supplier);
        constants.remove(channel);
        return this;
    }

    public SurfaceConfig replaceBody(boolean replaceBody) {
        this.replaceBody = replaceBody;
        return this;
    }

    public SurfaceConfig sampler(String name, Identifier texture) {
        samplers.put(name, texture);
        return this;
    }
}
