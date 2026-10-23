package com.example.surface;

import com.meekdev.amnetic.client.surface.HudSurface;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.reactive.Computed;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class HudDemo {

    private static final Identifier FONT = Identifier.fromNamespaceAndPath("example", "fonts/heavitas.ttf");

    private static final Signal<Float> ENERGY = new Signal<>(0.25f);
    private static final Motion<Float> BAR = Motion.spring(0.25f, 60f, 9f);
    private static final Computed<Integer> BAR_COLOR = new Computed<>(() -> {
        float v = BAR.value().get();
        int g = (int) (140 + 100 * v);
        int b = (int) (255 * (1f - v * 0.6f));
        return 0xFF000000 | (int) (80 + 175 * v) << 16 | Math.min(g, 255) << 8 | Math.max(b, 60);
    });

    private static HudSurface hud;
    private static boolean f4Down, gDown;

    private HudDemo() {}

    public static void init() {
        BAR.follow(ENERGY);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean f4 = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F4);
            if (f4 && !f4Down) toggle();
            f4Down = f4;
            boolean g = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_G);
            if (g && !gDown && hud != null) {
                ENERGY.set((float) Math.random());
            }
            gDown = g;
        });
    }

    private static void toggle() {
        if (hud != null) {
            hud.remove();
            hud = null;
            return;
        }
        hud = Surfaces.hud().onDraw(d -> {
            float w = 260, h = 86;
            float x = 24, y = d.height() - h - 24;

            d.font(FONT);
            d.shadow(x + 3, y + 5, w, h, 14, 12, 0x80000000);
            d.roundedRect(x, y, w, h, 14, 0xE0101418);
            d.border(x, y, w, h, 14, 1.5f, 0x50FFFFFF);

            d.text("ENERGY", x + 18, y + 14, 15, 0xFFFFFFFF);
            float v = BAR.value().peek();
            d.textCentered(String.format("%.0f%%", v * 100), x + w - 42, y + 21, 15, 0xFFB0E0FF);

            float barX = x + 18, barY = y + 46, barW = w - 36, barH = 16;
            d.roundedRect(barX, barY, barW, barH, 8, 0xFF2A2F36);
            float fill = Math.max(barW * Math.min(Math.max(v, 0f), 1f), barH);
            d.roundedRect(barX, barY, fill, barH, 8, BAR_COLOR.peek());
        });
    }
}
