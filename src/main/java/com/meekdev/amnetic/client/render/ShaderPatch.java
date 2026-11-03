package com.meekdev.amnetic.client.render;

import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ShaderPatch {
    private enum Mode { BEFORE, AFTER, REPLACE }

    private record Hunk(Mode mode, String anchor, List<String> lines) {
        String apply(String source) {
            String body = String.join("\n", lines);
            return switch (mode) {
                case BEFORE -> source.replace(anchor, body + "\n" + anchor);
                case AFTER -> source.replace(anchor, anchor + "\n" + body);
                case REPLACE -> source.replace(anchor, body);
            };
        }
    }

    private ShaderPatch() {}

    public static String apply(String source, Identifier patch, Set<String> switches) {
        String text = ShaderSwitches.apply(ShaderProgram.readRaw(patch), switches);
        for (Hunk hunk : parse(text, patch)) source = hunk.apply(source);
        return source;
    }

    private static List<Hunk> parse(String text, Identifier patch) {
        List<Hunk> hunks = new ArrayList<>();
        Hunk current = null;
        for (String line : text.split("\n")) {
            Mode mode = mode(line);
            if (mode != null) {
                String anchor = line.substring(line.indexOf(' ') + 1);
                current = new Hunk(mode, anchor, new ArrayList<>());
                hunks.add(current);
            } else if (current != null) {
                current.lines().add(line);
            } else if (!line.isBlank()) {
                throw new IllegalArgumentException("shader patch " + patch + " starts outside a hunk");
            }
        }
        return hunks;
    }

    private static Mode mode(String line) {
        if (line.startsWith("#before ")) return Mode.BEFORE;
        if (line.startsWith("#after ")) return Mode.AFTER;
        if (line.startsWith("#replace ")) return Mode.REPLACE;
        return null;
    }
}
