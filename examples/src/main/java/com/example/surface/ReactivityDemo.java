package com.example.surface;

import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.Lights;
import com.meekdev.amnetic.client.surface.reactive.Computed;
import com.meekdev.amnetic.client.surface.reactive.Effect;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class ReactivityDemo {

    private static final Signal<Float> ENERGY = new Signal<>(0f);
    private static final float[] TARGETS = {0f, 1f, 3f, 6f};

    private static Light light;
    private static Motion<Float> motion;
    private static Effect apply;
    private static int step;
    private static boolean f6Down;

    private ReactivityDemo() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) {
                teardown();
                return;
            }
            boolean down = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F6);
            if (down && !f6Down) {
                step = (step + 1) % TARGETS.length;
                setup(mc.player.position().x, mc.player.position().y + 2.0, mc.player.position().z);
                ENERGY.set(TARGETS[step]);
            }
            f6Down = down;
            if (light != null) {
                light.setPosition(mc.player.position().x, mc.player.position().y + 2.0, mc.player.position().z);
            }
        });
    }

    private static void setup(double x, double y, double z) {
        if (light != null) return;
        light = Lights.point(new Vec3(x, y, z), 1f, 0.8f, 0.5f, 14f, 0f);

        motion = Motion.spring(0f, 60f, 9f);
        motion.follow(ENERGY);

        Computed<Float> warmth = new Computed<>(() -> Math.min(motion.value().get() / 6f, 1f));

        apply = new Effect(() -> {
            float e = motion.value().get();
            float w = warmth.get();
            light.setIntensity(e);
            light.setColor(1f, 0.5f + 0.5f * w, 0.3f + 0.7f * w);
        });
    }

    private static void teardown() {
        if (apply != null) { apply.dispose(); apply = null; }
        if (motion != null) { motion.dispose(); motion = null; }
        if (light != null) { light.remove(); light = null; }
        step = 0;
        ENERGY.set(0f);
    }
}
