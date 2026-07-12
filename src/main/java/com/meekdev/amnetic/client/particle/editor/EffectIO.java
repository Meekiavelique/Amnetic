package com.meekdev.amnetic.client.particle.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public final class EffectIO {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/Particles");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private EffectIO() {}

    public static Path dir() {
        Path p = Minecraft.getInstance().gameDirectory.toPath().resolve("amnetic").resolve("particles");
        try { Files.createDirectories(p); } catch (IOException ignored) {}
        return p;
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir())) {
            s.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .forEach(f -> {
                        String n = f.getFileName().toString();
                        out.add(n.substring(0, n.length() - 5));
                    });
        } catch (IOException ignored) {}
        Collections.sort(out);
        return out;
    }

    public static boolean save(EffectDraft draft) {
        String stem = sanitize(draft.name);
        try {
            Files.writeString(dir().resolve(stem + ".json"), GSON.toJson(draft));
            return true;
        } catch (IOException e) {
            LOGGER.warn("failed to save particle effect '{}'", stem, e);
            return false;
        }
    }

    public static EffectDraft load(String name) {
        try {
            EffectDraft d = GSON.fromJson(Files.readString(dir().resolve(name + ".json")), EffectDraft.class);
            if (d != null && d.name == null) d.name = name;
            return d;
        } catch (Exception e) {
            LOGGER.warn("failed to load particle effect '{}'", name, e);
            return null;
        }
    }

    public static void delete(String name) {
        try { Files.deleteIfExists(dir().resolve(name + ".json")); } catch (IOException ignored) {}
    }

    private static String sanitize(String name) {
        String s = (name == null ? "" : name).trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return s.isEmpty() ? "effect" : s;
    }
}
