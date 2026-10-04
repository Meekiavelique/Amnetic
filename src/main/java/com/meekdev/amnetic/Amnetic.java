package com.meekdev.amnetic;

import com.meekdev.amnetic.client.AmneticClient;
//? fabric {
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
*///?}
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? forge {
/*@Mod(Amnetic.MOD_ID)
*///?}
//? fabric {
public class Amnetic implements ModInitializer {
//?} else {
/*public class Amnetic {
*///?}

    public static final String MOD_ID = "amnetic";

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic");

    //? fabric {
    @Override
    public void onInitialize() {
        warnIfNested();
    }
    //?} else {
    /*public Amnetic(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientEvents.install(context);
            AmneticClient.initialize();
        }
    }
    *///?}

    //? fabric {
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
    //?}
}
