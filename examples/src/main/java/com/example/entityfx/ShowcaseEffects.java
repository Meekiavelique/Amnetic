package com.example.entityfx;

import com.meekdev.amnetic.client.entityfx.EntityEffect;
import com.meekdev.amnetic.client.entityfx.EntityEffects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

public final class ShowcaseEffects {

    private static Identifier shader(String name) {
        return Identifier.fromNamespaceAndPath("example", "entityfx/" + name);
    }

    private ShowcaseEffects() {}

    public static EntityEffect glass(LivingEntity entity) {
        return glass(entity, 0.6f, 0.8f, 1.0f, 0.18f, 1.5f, 0.4f, 4f);
    }

    public static EntityEffect glass(LivingEntity entity, float r, float g, float b, float tintA,
                                     float ior, float refract, float fresnelPower) {
        Identifier glass = shader("glass");
        return EntityEffects.surface(entity, glass, glass, cfg -> cfg
                .sceneDepth(true)
                .uniform(0, r).uniform(1, g).uniform(2, b).uniform(3, tintA)
                .uniform(4, ior / 4f).uniform(5, refract).uniform(6, fresnelPower / 16f)
                .uniform(7, 1f));
    }

    public static EntityEffect hologram(LivingEntity entity) {
        return hologram(entity, 0.3f, 0.85f, 1.0f, 0.55f, 0.31f, 0.25f, 0.0f, 0.15f);
    }

    public static EntityEffect glitchHologram(LivingEntity entity) {
        return hologram(entity, 0.4f, 1.0f, 0.7f, 0.5f, 0.5f, 0.6f, 0.7f, 0.5f);
    }

    public static EntityEffect hologram(LivingEntity entity, float r, float g, float b, float alpha,
                                        float scanDensity256, float scanSpeed8, float glitch, float flicker) {
        Identifier holo = shader("hologram");
        return EntityEffects.surface(entity, holo, holo, cfg -> cfg
                .skin(true)
                .uniform(0, r).uniform(1, g).uniform(2, b).uniform(3, alpha)
                .uniform(4, scanDensity256).uniform(5, scanSpeed8).uniform(6, glitch).uniform(7, flicker)
                .uniform(8, 1f));
    }

    public static EntityEffect recolor(LivingEntity entity, float r, float g, float b) {
        return recolor(entity, r, g, b, 0.85f);
    }

    public static EntityEffect recolor(LivingEntity entity, float r, float g, float b, float detail) {
        Identifier recolor = shader("recolor");
        return EntityEffects.surface(entity, recolor, recolor, cfg -> cfg
                .skin(true).sceneColor(false)
                .uniform(0, r).uniform(1, g).uniform(2, b).uniform(3, detail)
                .uniform(4, 1f));
    }
}
