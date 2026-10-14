package com.meekdev.amnetic.platform;

import com.meekdev.amnetic.Amnetic;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
//? fabric {
import net.fabricmc.loader.api.FabricLoader;
//?} else {
/*import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
*///?}
import net.minecraft.client.Minecraft;

public final class Platform {

    private Platform() {}

    public static boolean isModLoaded(String id) {
        //? fabric {
        return FabricLoader.getInstance().isModLoaded(id);
        //?} else if >=26.1 {
        /*return ModList.isLoaded(id);
        *///?} else {
        /*return ModList.get().isLoaded(id);
        *///?}
    }

    public static boolean isDevelopmentEnvironment() {
        //? fabric {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
        //?} else {
        /*return !FMLEnvironment.production;
        *///?}
    }

    public static Path gameDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    public static List<Path> developmentResourceRoots() {
        if (!isDevelopmentEnvironment()) return List.of();
        List<Path> roots = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path", "").split(System.getProperty("path.separator"))) {
            Path path = Path.of(entry).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) roots.add(path);
        }
        try {
            URI location = Amnetic.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location).toAbsolutePath().normalize();
            if (Files.isDirectory(path) && !roots.contains(path)) roots.add(path);
        } catch (Exception ignored) {
        }
        return roots;
    }
}
