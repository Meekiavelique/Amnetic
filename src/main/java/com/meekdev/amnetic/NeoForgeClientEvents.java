//? neoforge {
/*package com.meekdev.amnetic;

import com.meekdev.amnetic.client.AmneticClient;
import com.meekdev.amnetic.client.render.LevelCamera;
import com.meekdev.amnetic.client.ui.AmneticEditorBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

final class NeoForgeClientEvents {
    private NeoForgeClientEvents() {}
    static void install(IEventBus modBus) {
        modBus.addListener(AmneticEditorBridge::registerKeyMapping);
        modBus.addListener(NeoForgeClientEvents::reload);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> AmneticClient.endClientTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> AmneticClient.disconnect());
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> AmneticClient.stopping());
        // AfterLevel has no active render pass, so native uploads and separate passes are permitted here.
        //? if <26.3 {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterLevel event) -> {
            LevelCamera camera = LevelCamera.of(event.getLevelRenderState().cameraRenderState);
            AmneticClient.beforeTranslucent(camera);
            AmneticClient.endMain(camera);
        });
        //?}
    }
    private static void reload(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Amnetic.MOD_ID, "native_shaders"),
                (ResourceManagerReloadListener) manager -> AmneticClient.reloadResources());
    }
}
*///?}
