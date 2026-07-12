package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.light.Light;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LightRegistry {

    public static final LightRegistry INSTANCE = new LightRegistry();

    private final List<Light> lights = new CopyOnWriteArrayList<>();

    private LightRegistry() {}

    public void add(Light light) { if (!lights.contains(light)) lights.add(light); }
    public void remove(Light light) { lights.remove(light); }
    public void clear() { lights.clear(); }

    public List<Light> all() { return lights; }
    public boolean isEmpty() { return lights.isEmpty(); }
}
