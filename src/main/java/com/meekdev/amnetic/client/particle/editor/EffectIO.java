package com.meekdev.amnetic.client.particle.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

public final class EffectIO {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/Particles");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private EffectIO() {}

    private static EffectPresetFiles files() {
        return new EffectPresetFiles(Minecraft.getInstance().gameDirectory.toPath());
    }

    public static Path dir() {
        try { return files().directory(); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public static List<String> list() {
        try { return files().list(); }
        catch (IOException e) {
            LOGGER.warn("Unable to list local particle presets", e);
            return List.of();
        }
    }

    public static boolean save(EffectDraft draft) {
        if (draft == null) return false;
        String stem = sanitize(draft.name);
        try {
            files().write(stem, GSON.toJson(draft));
            return true;
        } catch (IOException e) {
            LOGGER.warn("failed to save particle effect '{}'", stem, e);
            return false;
        }
    }

    public static EffectDraft load(String name) {
        try {
            EffectDraft d = GSON.fromJson(files().read(name), EffectDraft.class);
            if (d != null && d.name == null) d.name = name;
            return d;
        } catch (Exception e) {
            LOGGER.warn("failed to load particle effect '{}'", name, e);
            return null;
        }
    }

    public static void delete(String name) {
        try { files().delete(name); }
        catch (IOException e) { LOGGER.warn("Unable to delete local particle preset", e); }
    }

    private static String sanitize(String name) {
        String s = (name == null ? "" : name).trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return s.isEmpty() ? "effect" : s;
    }
}
