package com.meekdev.amnetic.client.render;

import com.meekdev.amnetic.platform.Platform;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL41C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProgramCache {
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/ProgramCache");
    private static final int VERSION = 1;
    private static final int MAX_FILES = 1024;
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Amnetic program cache");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static Boolean enabled;
    private static String driver;
    private static Path dir;
    private static boolean pruned;

    private ProgramCache() {}

    static boolean enabled() {
        if (enabled == null) {
            GLCapabilities caps = GL.getCapabilities();
            boolean supported = (caps.OpenGL41 || caps.GL_ARB_get_program_binary)
                    && GL11.glGetInteger(GL41C.GL_NUM_PROGRAM_BINARY_FORMATS) > 0;
            enabled = supported && !"false".equals(System.getProperty("amnetic.programCache"));
            if (enabled) {
                driver = GL11.glGetString(GL11.GL_VENDOR) + "|" + GL11.glGetString(GL11.GL_RENDERER) + "|"
                        + GL11.glGetString(GL11.GL_VERSION) + "|" + GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION);
                dir = Platform.gameDirectory().resolve("amnetic").resolve("program_cache");
            }
        }
        return enabled;
    }

    static String key(List<ProgramLink.Stage> stages, String extra) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update((VERSION + "\0" + driver + "\0" + extra + "\0").getBytes(StandardCharsets.UTF_8));
            for (ProgramLink.Stage stage : stages) {
                sha.update((stage.type() + "\0").getBytes(StandardCharsets.UTF_8));
                sha.update(stage.source().getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
            }
            return HexFormat.of().formatHex(sha.digest(), 0, 20);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    static void retrievable(int program) {
        GL41C.glProgramParameteri(program, GL41C.GL_PROGRAM_BINARY_RETRIEVABLE_HINT, GL11.GL_TRUE);
    }

    static int load(String key) {
        Path file = dir.resolve(key + ".bin");
        byte[] bytes;
        try {
            if (!Files.isRegularFile(file)) return 0;
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            return 0;
        }
        if (bytes.length <= 4) return 0;
        ByteBuffer data = MemoryUtil.memAlloc(bytes.length - 4);
        int program = GL20.glCreateProgram();
        try {
            int format = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            data.put(bytes, 4, bytes.length - 4).flip();
            GL41C.glProgramBinary(program, format, data);
        } finally {
            MemoryUtil.memFree(data);
        }
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            GL20.glDeleteProgram(program);
            WRITER.execute(() -> delete(file));
            return 0;
        }
        WRITER.execute(() -> touch(file));
        return program;
    }

    static void store(String key, int program) {
        int length = GL20.glGetProgrami(program, GL41C.GL_PROGRAM_BINARY_LENGTH);
        if (length <= 0) return;
        ByteBuffer data = MemoryUtil.memAlloc(length);
        byte[] bytes;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var written = stack.mallocInt(1);
            var format = stack.mallocInt(1);
            GL41C.glGetProgramBinary(program, written, format, data);
            int n = written.get(0);
            if (n <= 0) return;
            bytes = new byte[n + 4];
            ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(format.get(0));
            data.get(0, bytes, 4, n);
        } finally {
            MemoryUtil.memFree(data);
        }
        Path file = dir.resolve(key + ".bin");
        WRITER.execute(() -> write(file, bytes));
    }

    static boolean checked(String key) {
        Path file = dir.resolve(key + ".ok");
        if (!Files.isRegularFile(file)) return false;
        WRITER.execute(() -> touch(file));
        return true;
    }

    static void markChecked(String key) {
        Path file = dir.resolve(key + ".ok");
        WRITER.execute(() -> write(file, new byte[0]));
    }

    private static void write(Path file, byte[] bytes) {
        try {
            Files.createDirectories(file.getParent());
            Path part = file.resolveSibling(file.getFileName() + ".part");
            Files.write(part, bytes);
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.debug("could not save a program binary: {}", e.toString());
        }
        if (!pruned) {
            pruned = true;
            prune(file.getParent());
        }
    }

    private static void prune(Path folder) {
        try (Stream<Path> files = Files.list(folder)) {
            List<Path> all = files.filter(p -> !p.toString().endsWith(".part")).sorted(Comparator.comparing(ProgramCache::modified).reversed()).toList();
            for (int i = MAX_FILES; i < all.size(); i++) delete(all.get(i));
        } catch (IOException ignored) {
        }
    }

    private static FileTime modified(Path p) {
        try {
            return Files.getLastModifiedTime(p);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    private static void touch(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException ignored) {
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}
