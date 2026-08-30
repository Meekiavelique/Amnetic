package com.meekdev.amnetic.client.subsurface;

import com.meekdev.amnetic.client.material.ShadingModel;
import com.meekdev.amnetic.client.material.internal.MaterialParams;
import com.meekdev.amnetic.client.subsurface.internal.SubsurfacePass;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;

public final class Subsurface {

    private static final Identifier SNIPPET =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/material/subsurface.glsl");

    private static final Map<String, ShadingModel> PROFILES = new ConcurrentHashMap<>();
    private static final SubsurfaceSettings SETTINGS = new SubsurfaceSettings();

    private Subsurface() {}

    public static SubsurfaceSettings settings() { return SETTINGS; }
    public static boolean isEnabled() { return SETTINGS.isEnabled(); }
    public static void enable() { SETTINGS.enabled(true); }
    public static void disable() { SETTINGS.enabled(false); }

    public static void render() { SubsurfacePass.INSTANCE.render(SETTINGS); }
    public static void dispose() { SubsurfacePass.INSTANCE.dispose(); }

    public static Profile profile() {
        return new Profile();
    }

    public static final class Profile {

        private float strength = 1f;
        private float tintR = 1f, tintG = 1f, tintB = 1f;
        private float radius = 0.015f;
        private float thickness = 0.4f;
        private float nearAmp = 0f;
        private float nearRadius = 0.003f;
        private float power = 3f;

        private Profile() {}

        public Profile strength(float v) { strength = v; return this; }

        public Profile tint(float r, float g, float b) { tintR = r; tintG = g; tintB = b; return this; }

        public Profile radius(float metres) { radius = metres; return this; }

        public Profile thickness(float metres) { thickness = metres; return this; }

        public Profile nearField(float amplitude, float metres) {
            nearAmp = amplitude;
            nearRadius = metres;
            return this;
        }

        public Profile power(float p) { power = p; return this; }

        public ShadingModel build() {
            String key = String.format(Locale.ROOT, "%.5f/%.5f/%.5f/%.5f/%.5f/%.5f/%.5f/%.5f/%.5f",
                    strength, tintR, tintG, tintB, radius, thickness, nearAmp, nearRadius, power);
            return PROFILES.computeIfAbsent(key, ignored -> {
                ShadingModel model = ShadingModel.pbr().fragment(SNIPPET);
                MaterialParams.INSTANCE.set(model.id(),
                        tintR, tintG, tintB, strength,
                        radius, thickness, nearAmp, nearRadius,
                        power, 0f, 0f, 0f);
                return model;
            });
        }
    }

    public static ShadingModel skin() {
        return profile()
                .strength(1.0f).tint(0.85f, 0.35f, 0.28f)
                .radius(0.012f).thickness(0.40f)
                .nearField(0.6f, 0.003f).power(4.0f)
                .build();
    }

    public static ShadingModel wax() {
        return profile()
                .strength(0.9f).tint(0.95f, 0.85f, 0.62f)
                .radius(0.030f).thickness(0.80f)
                .nearField(0.4f, 0.006f).power(2.5f)
                .build();
    }

    public static ShadingModel foliage() {
        return profile()
                .strength(0.9f).tint(0.45f, 0.80f, 0.30f)
                .radius(0.008f).thickness(0.05f)
                .nearField(1.0f, 0.002f).power(3.0f)
                .build();
    }

    public static ShadingModel marble() {
        return profile()
                .strength(0.6f).tint(0.90f, 0.88f, 0.86f)
                .radius(0.020f).thickness(1.20f)
                .nearField(0.2f, 0.004f).power(6.0f)
                .build();
    }
}
