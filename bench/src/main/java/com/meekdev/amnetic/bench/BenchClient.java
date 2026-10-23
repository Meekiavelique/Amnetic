package com.meekdev.amnetic.bench;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.ChatFormatting;

public final class BenchClient implements ClientModInitializer {

    private static final float DEFAULT_SECONDS = 10f;

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> Scenes.init());
        WorldRenderEvents.END.register(ctx -> BenchRunner.INSTANCE.onFrame());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BenchRunner.INSTANCE.stop());
        AutoRun.install();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(client -> BenchRunner.INSTANCE.onClientTick());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                ClientCommandManager.literal("abench")
                        .then(ClientCommandManager.literal("list").executes(BenchClient::list))
                        .then(ClientCommandManager.literal("arena").executes(ctx -> {
                            if (BenchArena.build()) {
                                BenchRunner.chat("arena built around you", ChatFormatting.GREEN);
                            } else {
                                BenchRunner.chat("the arena needs a singleplayer world", ChatFormatting.RED);
                            }
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("stop").executes(ctx -> {
                            BenchRunner.INSTANCE.stop();
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("show")
                                .then(ClientCommandManager.argument("scene", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            Scenes.all().keySet().forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            BenchScene scene = Scenes.all().get(StringArgumentType.getString(ctx, "scene"));
                                            if (scene == null) return unknown(ctx);
                                            BenchRunner.INSTANCE.show(scene);
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("run")
                                .then(ClientCommandManager.argument("scene", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            b.suggest("all");
                                            Scenes.all().keySet().forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> run(ctx, DEFAULT_SECONDS))
                                        .then(ClientCommandManager.argument("seconds", FloatArgumentType.floatArg(2f, 120f))
                                                .executes(ctx -> run(ctx, FloatArgumentType.getFloat(ctx, "seconds"))))))));
    }

    private static int list(CommandContext<FabricClientCommandSource> ctx) {
        for (BenchScene s : Scenes.all().values()) {
            BenchRunner.chat(s.name() + " - " + s.description(), ChatFormatting.GRAY);
        }
        return 1;
    }

    private static int run(CommandContext<FabricClientCommandSource> ctx, float seconds) {
        String name = StringArgumentType.getString(ctx, "scene");
        List<BenchScene> scenes = new ArrayList<>();
        if (name.equals("all")) {
            scenes.addAll(Scenes.all().values());
        } else {
            BenchScene scene = Scenes.all().get(name);
            if (scene == null) return unknown(ctx);
            BenchScene base = Scenes.all().get("baseline");
            if (scene != base) scenes.add(base);
            scenes.add(scene);
        }
        BenchRunner.INSTANCE.run(scenes, seconds);
        return 1;
    }

    private static int unknown(CommandContext<FabricClientCommandSource> ctx) {
        BenchRunner.chat("unknown scene, see /abench list", ChatFormatting.RED);
        return 0;
    }
}
