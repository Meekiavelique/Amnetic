//? forge {
package com.meekdev.amnetic;

import com.meekdev.amnetic.client.AmneticClient;
import com.meekdev.amnetic.client.render.LevelCamera;
import com.meekdev.amnetic.client.ui.AmneticEditorBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//? if <1.21.9 {
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
//?} else if >=1.21.9 {
/*import com.mojang.blaze3d.framegraph.FramePass;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.FramePassManager;
import net.minecraftforge.client.event.AddFramePassEvent;
import net.minecraftforge.event.TickEvent;
*///?}
//? if >=26.1 {
/*import net.minecraft.client.renderer.state.level.LevelRenderState;
*///?} else if >=1.21.9 {
/*import net.minecraft.client.renderer.state.LevelRenderState;
*///?}

final class ForgeClientEvents {

    private static boolean framePassRegistered;

    private ForgeClientEvents() {}

    static void install(FMLJavaModLoadingContext context) {
        //? if <1.21.9 {
        IEventBus modBus = context.getModEventBus();
        modBus.addListener(AmneticEditorBridge::registerKeyMapping);
        modBus.addListener(ForgeClientEvents::registerReloadListener);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEvents::tick);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEvents::disconnect);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEvents::renderLevel);
        //?} else if >=1.21.9 {
        /*RegisterKeyMappingsEvent.BUS.addListener(AmneticEditorBridge::registerKeyMapping);
        RegisterClientReloadListenersEvent.BUS.addListener(ForgeClientEvents::registerReloadListener);
        TickEvent.ClientTickEvent.Post.BUS.addListener(event ->
                AmneticClient.endClientTick(Minecraft.getInstance()));
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> AmneticClient.disconnect());
        AddFramePassEvent.BUS.addListener(ForgeClientEvents::registerFramePass);
        *///?}
    }

    private static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> AmneticClient.reloadResources());
    }

    //? if <1.21.9 {
    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            AmneticClient.endClientTick(Minecraft.getInstance());
        }
    }

    private static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        AmneticClient.disconnect();
    }

    private static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            AmneticClient.beforeTranslucent(LevelCamera.main());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            AmneticClient.endMain(LevelCamera.main());
        }
    }
    //?}

    //? if >=26.1 {
    /*private static void registerFramePass(AddFramePassEvent event) {
        if (framePassRegistered) return;
        framePassRegistered = true;
        event.addPass(Identifier.fromNamespaceAndPath(Amnetic.MOD_ID, "main"), new FramePassManager.PassDefinition() {
            @Override
            public void extracts(LevelTargetBundle targets, FramePass pass, DeltaTracker deltaTracker) {
                targets.main = pass.readsAndWrites(targets.main);
            }

            @Override
            public void executes(LevelRenderState state) {
                LevelCamera camera = LevelCamera.of(state.cameraRenderState);
                AmneticClient.beforeTranslucent(camera);
                AmneticClient.endMain(camera);
            }
        });
    }
    *///?} else if >=1.21.9 {
    /*private static void registerFramePass(AddFramePassEvent event) {
        if (framePassRegistered) return;
        framePassRegistered = true;
        event.addPass(Identifier.fromNamespaceAndPath(Amnetic.MOD_ID, "main"), new FramePassManager.PassDefinition() {
            @Override
            public void extracts(LevelTargetBundle targets, FramePass pass, DeltaTracker deltaTracker) {
                targets.main = pass.readsAndWrites(targets.main);
            }

            @Override
            public void executes(LevelRenderState state) {
                LevelCamera camera = LevelCamera.main();
                AmneticClient.beforeTranslucent(camera);
                AmneticClient.endMain(camera);
            }
        });
    }
    *///?}
}
//?}
