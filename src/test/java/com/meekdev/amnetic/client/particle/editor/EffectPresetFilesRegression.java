package com.meekdev.amnetic.client.particle.editor;

import java.io.IOException;
import java.util.List;
import java.nio.file.*;
import java.util.Comparator;

public final class EffectPresetFilesRegression {
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("amnetic-presets-test-");
        try {
            Path game = Files.createDirectory(temporary.resolve("game"));
            Path outside = Files.writeString(temporary.resolve("outside.json"), "untouched");
            EffectPresetFiles files = new EffectPresetFiles(game);
            files.write("Greek-fire_01", "{\"name\":\"fire\"}");
            require(files.read("Greek-fire_01").contains("fire"), "round trip");
            files.write("Greek-fire_01", "{}");
            require(files.read("Greek-fire_01").equals("{}"), "atomic replacement");
            require(files.list().equals(List.of("Greek-fire_01")), "listing");
            for (String name : new String[] {null, "", "..", "../outside", "..\\outside", "/outside",
                    "C:\\outside", "file:stream", "CON", "aux.txt", "LPT1", "trailing.", "x".repeat(129)}) {
                rejected(() -> files.read(name));
                rejected(() -> files.write(name, "damage"));
                rejected(() -> files.delete(name));
            }
            rejected(() -> files.write("large", "x".repeat(EffectPresetFiles.MAX_BYTES + 1)));
            Files.writeString(files.directory().resolve("large.json"), "x".repeat(EffectPresetFiles.MAX_BYTES + 1));
            rejected(() -> files.read("large"));
            Path linked = files.directory().resolve("linked.json");
            try {
                Files.createSymbolicLink(linked, outside);
                rejected(() -> files.read("linked"));
                rejected(() -> files.write("linked", "damage"));
                rejected(() -> files.delete("linked"));
                require(!files.list().contains("linked"), "symlink listing");
                Files.delete(linked);
                Path otherGame = Files.createDirectory(temporary.resolve("linked-game"));
                Files.createSymbolicLink(otherGame.resolve("amnetic"), files.directory());
                rejected(() -> new EffectPresetFiles(otherGame).write("escaped", "damage"));
                System.out.println("PASS: file and parent symlink rejection");
            } catch (UnsupportedOperationException | FileSystemException e) {
                System.out.println("SKIP: symbolic links unavailable: " + e.getMessage());
            }
            Files.createLink(linked, outside);
            files.write("linked", "{}");
            require(Files.readString(outside).equals("untouched"), "external hard-link target changed");
            files.delete("Greek-fire_01");
            files.delete("missing");
            require(!files.list().contains("Greek-fire_01"), "delete");
            require(Files.readString(outside).equals("untouched"), "outside sentinel changed");
            System.out.println("PASS: preset round trips, replacement, traversal/absolute/ADS/device names, size limits, hard links and deletion");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private interface Action { void run() throws IOException; }
    private static void rejected(Action action) throws IOException {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Unsafe operation accepted");
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
