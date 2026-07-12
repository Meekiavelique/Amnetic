package com.meekdev.amnetic.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AmneticEditorBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/Editor");
    private static final String IMGUIMC = "imguimc";

    private static KeyMapping toggleKey;
    private static boolean available;
    private static boolean warned;

    private AmneticEditorBridge() {}

    public static void init() {
        toggleKey = new KeyMapping(
                "key.amnetic.editor",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC);
        KeyMappingHelper.registerKeyMapping(toggleKey);

        if (FabricLoader.getInstance().isModLoaded(IMGUIMC)) {
            AmneticEditor.init();
            available = true;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private static void tick() {
        if (toggleKey == null) return;
        boolean toggled = false;
        while (toggleKey.consumeClick()) toggled = true;
        if (!toggled) return;

        if (available) {
            AmneticEditor.toggle();
        } else if (!warned) {
            warned = true;
            LOGGER.info("Install the ImGuiMC mod to use the Amnetic editor overlay.");
        }
    }
}
