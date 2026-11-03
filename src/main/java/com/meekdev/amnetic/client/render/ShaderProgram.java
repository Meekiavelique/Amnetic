package com.meekdev.amnetic.client.render;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ShaderProgram implements AutoCloseable {

    private static final Map<Identifier, Supplier<String>> VIRTUAL_SOURCES = new ConcurrentHashMap<>();

    public static void registerVirtualSource(Identifier id, Supplier<String> source) {
        VIRTUAL_SOURCES.put(id, source);
    }

    private boolean closed, warmFailed;
    private ProgramLink link;

    // starts every program nobody used yet and takes in the ones the driver finished, so the first use
    // does not wait on a link mid game. false once everything is linked
    public static boolean warm() {
        boolean worked = false;
        int starts = ProgramLink.parallel() ? Integer.MAX_VALUE : 1;
        for (ShaderProgram p : new ArrayList<>(LIVE.keySet())) {
            if (p == null || p.closed || p.program != 0 || p.warmFailed) continue;
            worked = true;
            try {
                if (p.link == null) {
                    if (starts-- > 0) p.start();
                } else if (p.link.ready()) {
                    p.ensure();
                }
            } catch (RuntimeException e) {
                p.warmFailed = true;
                p.link = null;
            }
        }
        return worked;
    }

    private final Identifier vshId;
    private final Identifier fshId;
    private int program;
    private int vao;

    private final Map<String, Integer> uniformLocations = new HashMap<>();
    private final float[] mat4Scratch = new float[16];
    private FloatBuffer matArrayScratch;

    private int loc(String name) {
        Integer cached = uniformLocations.get(name);
        if (cached != null) {
            return cached;
        }
        int l = GlStateManager._glGetUniformLocation(program, name);
        uniformLocations.put(name, l);
        return l;
    }

    private static final Map<ShaderProgram, Boolean> LIVE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile long revision;

    public static long revision() { return revision; }

    public static void invalidateAll() {
        revision++;
        synchronized (LIVE) {
            for (ShaderProgram sp : LIVE.keySet()) sp.invalidate();
        }
    }

    public ShaderProgram(Identifier vshId, Identifier fshId) {
        this.vshId = vshId;
        this.fshId = fshId;
        LIVE.put(this, Boolean.TRUE);
    }

    private boolean dirty;
    private long retryAfter;

    public void invalidate() {
        if (link != null) {
            link.delete();
            link = null;
        }
        warmFailed = false;
        dirty = true;
        retryAfter = 0;
    }

    private void start() {
        link = ProgramLink.builder(vshId + "/" + fshId)
                .vertex(loadWithIncludes(vshId), vshId)
                .fragment(loadWithIncludes(fshId), fshId)
                .soon()
                .start();
    }

    private void ensure() {
        if (program != 0 && (!dirty || System.nanoTime() < retryAfter)) return;
        if (link == null) start();
        ProgramLink linking = link;
        if (program != 0 && !linking.ready()) return;
        link = null;
        if (linking.failed()) {
            if (program != 0) {
                retryAfter = System.nanoTime() + 1_000_000_000L;
                LoggerFactory.getLogger("Amnetic/Shader").debug("Shader reload deferred for {}", fshId);
                return;
            }
            int stage = linking.failedStage();
            Identifier id = stage == 0 ? vshId : fshId;
            String dump = stage < 0 ? null : dumpSource(id, loadWithIncludes(id), linking.problem());
            throw new RuntimeException("Failed to " + (stage < 0 ? "link" : "compile") + " shader (" + vshId + "/" + fshId + "): "
                    + linking.problem() + (dump == null ? "" : "\nfull source written to " + dump));
        }
        if (program != 0) GlStateManager.glDeleteProgram(program);
        program = linking.program();
        if (vao == 0) vao = GlStateManager._glGenVertexArrays();
        uniformLocations.clear();
        dirty = false;
    }

    public void begin() {
        ensure();
        GlStateManager._glUseProgram(program);
        GlStateManager._glBindVertexArray(vao);
    }

    public void draw() {
        // Other render passes can bind VAO 0 directly while Mojang's cached
        // vertex-array state still points at this fullscreen VAO.
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    public void setSampler(String name, int unit) {
        int loc = loc(name);
        if (loc != -1) GL20.glUniform1i(loc, unit);
    }

    public void setFloat(String name, float value) {
        int loc = loc(name);
        if (loc != -1) GL20.glUniform1f(loc, value);
    }

    public void setVec2(String name, float x, float y) {
        int loc = loc(name);
        if (loc != -1) GL20.glUniform2f(loc, x, y);
    }

    public void setVec3(String name, float x, float y, float z) {
        int loc = loc(name);
        if (loc != -1) GL20.glUniform3f(loc, x, y, z);
    }

    public void setVec4(String name, float x, float y, float z, float w) {
        int loc = loc(name);
        if (loc != -1) GL20.glUniform4f(loc, x, y, z, w);
    }

    public void setVec2i(String name, int x, int y) {
        int loc = loc(name);
        if (loc != -1) GL20.glUniform2i(loc, x, y);
    }

    public void setInt(String name, int value) {
        int loc = loc(name);
        if (loc != -1) GL20.glUniform1i(loc, value);
    }

    public void setMatrix4Array(String name, float[] data, int count) {
        int loc = loc(name);
        if (loc == -1) loc = loc(name + "[0]");
        if (loc == -1 || count <= 0) return;
        int floats = Math.min(data.length, count * 16);
        if (matArrayScratch == null || matArrayScratch.capacity() < floats) {
            matArrayScratch = BufferUtils.createFloatBuffer(floats);
        }
        matArrayScratch.clear();
        matArrayScratch.put(data, 0, floats).flip();
        GL20.glUniformMatrix4fv(loc, false, matArrayScratch);
    }

    public void setVec4Array(String name, float[] data, int count) {
        int loc = loc(name);
        if (loc == -1) loc = loc(name + "[0]");
        if (loc == -1 || count <= 0) return;
        int floats = Math.min(data.length, count * 4);
        if (matArrayScratch == null || matArrayScratch.capacity() < floats) {
            matArrayScratch = BufferUtils.createFloatBuffer(floats);
        }
        matArrayScratch.clear();
        matArrayScratch.put(data, 0, floats).flip();
        GL20.glUniform4fv(loc, matArrayScratch);
    }

    public void setMatrix4(String name, Matrix4f matrix) {
        int loc = loc(name);
        if (loc == -1) return;
        matrix.get(mat4Scratch);
        GL20.glUniformMatrix4fv(loc, false, mat4Scratch);
    }

    @Override
    public void close() {
        closed = true;
        if (link != null) {
            link.delete();
            link = null;
        }
        if (program != 0) {
            GlStateManager.glDeleteProgram(program);
            program = 0;
        }
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
        uniformLocations.clear();
    }

    private static String dumpSource(Identifier id, String src, String log) {
        try {
            Path dir = Path.of("amnetic-shader-dumps");
            Files.createDirectories(dir);
            Path out = dir.resolve(id.toString().replaceAll("[^A-Za-z0-9._-]", "_") + ".glsl");
            StringBuilder sb = new StringBuilder();
            sb.append("// ").append(id).append('\n');
            for (String line : log.split("\n")) sb.append("// ").append(line).append('\n');
            sb.append('\n');
            String[] lines = src.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                sb.append(String.format("%5d| %s%n", i + 1, lines[i]));
            }
            Files.writeString(out, sb.toString());
            return out.toAbsolutePath().toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String loadWithIncludes(Identifier id) {
        StringBuilder out = new StringBuilder();
        resolve(id, out, new LinkedHashSet<>());
        if (VanillaCompat.reversedDepth()) defineAfterVersion(out, "AMNETIC_REVERSED_DEPTH");
        return out.toString();
    }

    // lets shared shaders tell which way depth runs, common/depth.glsl reads it
    private static void defineAfterVersion(StringBuilder src, String name) {
        int version = src.indexOf("#version");
        if (version < 0) return;
        int end = src.indexOf("\n", version);
        if (end < 0) return;
        src.insert(end + 1, "#define " + name + "\n");
    }

    private static void resolve(Identifier id, StringBuilder out, Set<Identifier> seen) {
        if (!seen.add(id)) return;
        for (String line : loadRaw(id).split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include") || trimmed.startsWith("#moj_import")) {
                int left = trimmed.indexOf('<'), right = trimmed.indexOf('>');
                if (left >= 0 && right > left) {
                    Identifier include = Identifier.parse(trimmed.substring(left + 1, right));
                    resolve(include.withPath(path -> "shaders/include/" + path), out, seen);
                    continue;
                }
                int a = trimmed.indexOf('"');
                int b = trimmed.lastIndexOf('"');
                if (a >= 0 && b > a) {
                    resolve(Identifier.parse(trimmed.substring(a + 1, b)), out, seen);
                    continue;
                }
            }
            out.append(line).append('\n');
        }
    }

    public static String readSource(Identifier id) {
        return loadWithIncludes(id);
    }

    public static String readRaw(Identifier id) {
        return loadRaw(id);
    }

    private static String loadRaw(Identifier id) {
        Supplier<String> virtual = VIRTUAL_SOURCES.get(id);
        if (virtual != null) return virtual.get();
        Optional<Resource> opt = Minecraft.getInstance().getResourceManager().getResource(id);
        if (opt.isEmpty()) {
            throw new RuntimeException("Shader not found: " + id);
        }
        try (InputStream is = opt.get().open()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader " + id, e);
        }
    }
}
