package com.meekdev.amnetic.bench;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

final class AutoRun {

    private static final int ARENA_DELAY_TICKS = 100;
    private static final int RUN_DELAY_TICKS = 60;

    private static String spec;
    private static int ticksInWorld;
    private static boolean arenaBuilt;
    private static boolean started;

    private AutoRun() {}

    static void install() {
        spec = System.getProperty("amnetic.bench.autorun");
        if (spec == null || spec.isBlank()) return;
        ClientTickEvents.END_CLIENT_TICK.register(AutoRun::tick);
    }

    private static void tick(Minecraft mc) {
        if (started || mc.player == null || mc.level == null) return;
        ticksInWorld++;
        if (!arenaBuilt && ticksInWorld >= ARENA_DELAY_TICKS) {
            BenchArena.build();
            arenaBuilt = true;
        }
        if (ticksInWorld < ARENA_DELAY_TICKS + RUN_DELAY_TICKS) return;
        started = true;
        String[] parts = spec.split(":");
        float seconds = parts.length > 1 ? Float.parseFloat(parts[1]) : 10f;
        List<BenchScene> scenes = new ArrayList<>();
        for (String name : parts[0].split(",")) {
            if (name.equals("all")) scenes.addAll(Scenes.all().values());
            else if (Scenes.all().containsKey(name)) scenes.add(Scenes.all().get(name));
        }
        if (Boolean.getBoolean("amnetic.bench.autoquit")) BenchRunner.INSTANCE.onFinished(mc::stop);
        BenchRunner.INSTANCE.run(scenes, seconds);
    }
}
