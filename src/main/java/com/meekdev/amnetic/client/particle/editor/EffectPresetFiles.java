package com.meekdev.amnetic.client.particle.editor;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;


final class EffectPresetFiles {
    static final int MAX_BYTES = 1024 * 1024;
    private static final Pattern STEM = Pattern.compile("[a-zA-Z0-9_-][a-zA-Z0-9._-]{0,127}");
    private final Path gameDirectory;

    EffectPresetFiles(Path gameDirectory) { this.gameDirectory = gameDirectory; }

    static boolean validName(String name) {
        if (name == null || !STEM.matcher(name).matches() || name.endsWith(".")) return false;
        String base = name.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        return !base.matches("CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9]");
    }

    Path directory() throws IOException {
        Path root = gameDirectory.toRealPath();
        Path library = checkedDirectory(root.resolve("amnetic"));
        return checkedDirectory(library.resolve("particles"));
    }

    private static Path checkedDirectory(Path path) throws IOException {
        try { Files.createDirectory(path); } catch (FileAlreadyExistsException ignored) { }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().equals(path))
            throw new IOException("Preset directory must be a local directory, not a link");
        return path;
    }

    private Path file(String name) throws IOException {
        if (!validName(name)) throw new IOException("Invalid preset name");
        Path root = directory();
        Path target = root.resolve(name + ".json").normalize();
        if (!root.equals(target.getParent())) throw new IOException("Preset path escapes its directory");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || !target.toRealPath().equals(target)))
            throw new IOException("Preset must be a regular local file");
        return target;
    }

    List<String> list() throws IOException {
        try (var paths = Files.list(directory())) {
            return paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - 5)).filter(EffectPresetFiles::validName)
                    .sorted().toList();
        }
    }

    String read(String name) throws IOException {
        Path target = file(name);
        try (var channel = Files.newByteChannel(target, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
             var input = Channels.newInputStream(channel)) {
            if (channel.size() > MAX_BYTES) throw new IOException("Preset exceeds 1 MiB");
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Preset exceeds 1 MiB");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    void write(String name, String json) throws IOException {
        Path target = file(name);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Preset exceeds 1 MiB");
        Path temporary = Files.createTempFile(target.getParent(), "effect-", ".tmp");
        try {
            Files.write(temporary, bytes, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    void delete(String name) throws IOException { Files.deleteIfExists(file(name)); }
}
