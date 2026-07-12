package com.meekdev.amnetic;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Amnetic implements ModInitializer {

    public static final String MOD_ID = "amnetic";

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic");

    @Override
    public void onInitialize() {
        warnIfNested();
    }

    private static void warnIfNested() {
        FabricLoader.getInstance().getModContainer(MOD_ID)
                .flatMap(ModContainer::getContainingMod)
                .ifPresent(parent -> {
                    String parentId = parent.getMetadata().getId();
                    String parentName = parent.getMetadata().getName();
                    LOGGER.warn(" Amnetic was loaded as a bundled dependency of '{}' ({}).", parentName, parentId);
                    LOGGER.warn(" Amnetic is meant to be installed as a SEPARATE standalone mod (e.g. from Modrinth).");
                    LOGGER.warn(" Bundling it is not supported: it makes the install hard to support, and when");
                    LOGGER.warn(" multiple mods each bundle their own copy, version mismatches cause conflicts.");
                    LOGGER.warn(" Please please please install Amnetic alongside '{}' instead of nesting it.", parentId);
                });
    }
}
