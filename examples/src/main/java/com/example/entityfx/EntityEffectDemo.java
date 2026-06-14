package com.example.entityfx;

import com.meekdev.amnetic.client.entityfx.EntityEffect;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.lwjgl.glfw.GLFW;

public final class EntityEffectDemo {

    private static EntityEffect current;
    private static int mode;
    private static boolean f7Down;

    private EntityEffectDemo() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) {
                if (current != null) { current.remove(); current = null; }
                return;
            }
            boolean down = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F7);
            if (down && !f7Down) {
                mode = (mode + 1) % 4;
                if (current != null) { current.remove(); current = null; }
                current = switch (mode) {
                    case 1 -> ShowcaseEffects.glass(mc.player);
                    case 2 -> ShowcaseEffects.hologram(mc.player);
                    case 3 -> ShowcaseEffects.glitchHologram(mc.player);
                    default -> null;
                };
            }
            f7Down = down;
        });
    }
}
