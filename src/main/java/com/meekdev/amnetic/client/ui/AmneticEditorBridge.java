package com.meekdev.amnetic.client.ui;

import com.meekdev.amnetic.platform.Platform;
import com.mojang.blaze3d.platform.InputConstants;
//? fabric {
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
//?} else if neoforge {
/*import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
*///?} else {
/*import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
*///?}
import net.minecraft.client.KeyMapping;
import com.meekdev.amnetic.client.compat.KeyInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//? if >=26.2 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
//?}

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
                InputConstants.Type.KEYBOARD,
                KeyInput.KEY_RIGHT_SHIFT,
                //? if >=1.21.9 {
                KeyMapping.Category.MISC);
                //?} else {
                /*"key.categories.misc");
                *///?}
        //? fabric && >=26.1 {
        KeyMappingHelper.registerKeyMapping(toggleKey);
        //?} else if fabric {
        /*KeyBindingHelper.registerKeyBinding(toggleKey);
        *///?}

        //? if >=26.2 {
        AmneticEditor.init();
        available = true;
        //?} else if fabric && >=1.21 {
        /*if (Platform.isModLoaded(IMGUIMC)) {
            AmneticEditor.init();
            available = true;
        }
        *///?}
    }

    //? forge || neoforge {
    /*public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(toggleKey);
    }
    *///?}

    public static void tick() {
        if (toggleKey == null) return;
        boolean toggled = false;
        while (toggleKey.consumeClick()) toggled = true;
        if (!toggled) return;

        //? if >=26.2 || (fabric && >=1.21) {
        if (available) {
            AmneticEditor.toggle();
            return;
        }
        //?}
        if (!warned) {
            warned = true;
            LOGGER.info("Install the ImGuiMC mod to use the Amnetic editor overlay.");
        }
    }

    //? if >=26.2 {
    public static boolean keyPressed(KeyEvent event) {
        if(Minecraft.getInstance().gui.screen()!=null && !AmneticEditor.isEnabled())return false;
        if (!available || toggleKey == null || !toggleKey.matches(event)) return false;
        AmneticEditor.toggle();
        return true;
    }
    //?}
}
