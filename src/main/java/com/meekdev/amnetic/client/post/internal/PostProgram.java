package com.meekdev.amnetic.client.post.internal;

import com.meekdev.amnetic.client.post.UniformValue;
import com.mojang.blaze3d.opengl.GlStateManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

final class PostProgram implements AutoCloseable {

    static final Identifier SCREENQUAD = Identifier.fromNamespaceAndPath("minecraft", "core/screenquad");

    private static final Identifier SCREENQUAD_FILE = Identifier.fromNamespaceAndPath("amnetic", "shaders/post/screenquad.vsh");
    private static final Identifier GLOBALS_FILE = Identifier.fromNamespaceAndPath("amnetic", "shaders/post/globals.glsl");

    private static final int FIRST_BINDING = 16;

    private record Member(String name, int offset) {}

    private static final class Block {
        final int index;
        final int binding;
        final int size;
        final List<Member> members = new ArrayList<>();
        final int buffer;
        final ByteBuffer data;

        Block(int index, int binding, int size) {
            this.index = index;
            this.binding = binding;
            this.size = size;
            this.data = MemoryUtil.memCalloc(size).order(ByteOrder.nativeOrder());
            this.buffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, buffer);
            GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, size, GL15.GL_DYNAMIC_DRAW);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
        }
    }

    private final int program;
    private final int vao;
    private final Map<String, Block> blocks = new HashMap<>();
    private final Map<String, Block> memberBlocks = new HashMap<>();
    private final Map<String, Integer> looseLocations = new HashMap<>();

    PostProgram(Identifier vertexShader, Identifier fragmentShader) {
        String vsh = SCREENQUAD.equals(vertexShader) ? read(SCREENQUAD_FILE) : load(vertexShader, "vsh");
        String fsh = load(fragmentShader, "fsh");
        int vs = compile(GL20.GL_VERTEX_SHADER, vsh, vertexShader);
        int fs;
        try {
            fs = compile(GL20.GL_FRAGMENT_SHADER, fsh, fragmentShader);
        } catch (RuntimeException e) {
            GlStateManager.glDeleteShader(vs);
            throw e;
        }
        int prog = GlStateManager.glCreateProgram();
        GlStateManager.glAttachShader(prog, vs);
        GlStateManager.glAttachShader(prog, fs);
        GlStateManager.glLinkProgram(prog);
        GlStateManager.glDeleteShader(vs);
        GlStateManager.glDeleteShader(fs);
        if (GlStateManager.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GlStateManager.glGetProgramInfoLog(prog, 4096);
            GlStateManager.glDeleteProgram(prog);
            throw new RuntimeException("failed to link post program " + vertexShader + " / " + fragmentShader + ": " + log);
        }
        program = prog;
        vao = GL30.glGenVertexArrays();
        reflect();
    }

    private void reflect() {
        int blockCount = GL31.glGetProgrami(program, GL31.GL_ACTIVE_UNIFORM_BLOCKS);
        Block[] byIndex = new Block[blockCount];
        for (int i = 0; i < blockCount; i++) {
            String name = GL31.glGetActiveUniformBlockName(program, i);
            int size = GL31.glGetActiveUniformBlocki(program, i, GL31.GL_UNIFORM_BLOCK_DATA_SIZE);
            Block block = new Block(i, FIRST_BINDING + i, size);
            GL31.glUniformBlockBinding(program, i, block.binding);
            blocks.put(name, block);
            byIndex[i] = block;
        }
        int uniformCount = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        for (int i = 0; i < uniformCount; i++) {
            String name = GL31.glGetActiveUniformName(program, i);
            if (name.endsWith("[0]")) name = name.substring(0, name.length() - 3);
            int blockIndex = GL31.glGetActiveUniformsi(program, i, GL31.GL_UNIFORM_BLOCK_INDEX);
            if (blockIndex < 0) {
                looseLocations.put(name, GL20.glGetUniformLocation(program, name));
                continue;
            }
            int offset = GL31.glGetActiveUniformsi(program, i, GL31.GL_UNIFORM_OFFSET);
            Block block = byIndex[blockIndex];
            block.members.add(new Member(name, offset));
            memberBlocks.put(name, block);
        }
        for (Block block : blocks.values()) block.members.sort(Comparator.comparingInt(Member::offset));
    }

    void begin() {
        GlStateManager._glUseProgram(program);
        GL30.glBindVertexArray(vao);
    }

    boolean hasSampler(String name) {
        return looseLocations.containsKey(name);
    }

    void setSampler(String name, int unit) {
        Integer loc = looseLocations.get(name);
        if (loc != null && loc >= 0) GL20.glUniform1i(loc, unit);
    }

    boolean setBlock(String name, List<UniformValue> values) {
        Block block = blocks.get(name);
        if (block == null) return false;
        for (int i = 0; i < values.size() && i < block.members.size(); i++) {
            values.get(i).write(block.data, block.members.get(i).offset());
        }
        return true;
    }

    void set(String name, UniformValue value) {
        Block block = memberBlocks.get(name);
        if (block != null) {
            for (Member m : block.members) {
                if (m.name().equals(name)) {
                    value.write(block.data, m.offset());
                    return;
                }
            }
        }
        Integer loc = looseLocations.get(name);
        if (loc == null || loc < 0) return;
        if (value instanceof UniformValue.FloatUniform v) GL20.glUniform1f(loc, v.value());
        else if (value instanceof UniformValue.IntUniform v) GL20.glUniform1i(loc, v.value());
        else if (value instanceof UniformValue.Vec2Uniform v) GL20.glUniform2f(loc, v.value().x(), v.value().y());
        else if (value instanceof UniformValue.Vec3Uniform v) GL20.glUniform3f(loc, v.value().x(), v.value().y(), v.value().z());
        else if (value instanceof UniformValue.IVec3Uniform v) GL20.glUniform3i(loc, v.value().x(), v.value().y(), v.value().z());
        else if (value instanceof UniformValue.Vec4Uniform v) GL20.glUniform4f(loc, v.value().x(), v.value().y(), v.value().z(), v.value().w());
        else if (value instanceof UniformValue.Matrix4x4Uniform v) {
            float[] m = new float[16];
            v.value().get(m);
            GL20.glUniformMatrix4fv(loc, false, m);
        }
    }

    void setSlot(String name, List<UniformValue> values) {
        if (setBlock(name, values)) return;
        if (values.size() == 1) set(name, values.get(0));
    }

    void upload() {
        for (Block block : blocks.values()) {
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, block.buffer);
            GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0, block.data);
            GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, block.binding, block.buffer);
        }
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
    }

    void end() {
        for (Block block : blocks.values()) {
            GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, block.binding, 0);
        }
    }

    @Override
    public void close() {
        for (Block block : blocks.values()) {
            GL15.glDeleteBuffers(block.buffer);
            MemoryUtil.memFree(block.data);
        }
        blocks.clear();
        GL30.glDeleteVertexArrays(vao);
        GlStateManager.glDeleteProgram(program);
    }

    private static String load(Identifier id, String extension) {
        StringBuilder out = new StringBuilder();
        Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "shaders/" + id.getPath() + "." + extension);
        append(file, read(file), out, new HashSet<>(), true);
        return out.toString();
    }

    private static void append(Identifier file, String source, StringBuilder out, Set<Identifier> seen, boolean root) {
        if (!seen.add(file)) return;
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (!root && trimmed.startsWith("#version")) continue;
            if (trimmed.startsWith("#moj_import")) {
                int a = trimmed.indexOf('<');
                int b = trimmed.lastIndexOf('>');
                if (a < 0 || b <= a) {
                    a = trimmed.indexOf('"');
                    b = trimmed.lastIndexOf('"');
                }
                if (a >= 0 && b > a) {
                    Identifier include = Identifier.parse(trimmed.substring(a + 1, b));
                    Identifier includeFile = Identifier.fromNamespaceAndPath(include.getNamespace(), "shaders/include/" + include.getPath());
                    Optional<String> body = tryRead(includeFile);
                    if (body.isPresent()) {
                        append(includeFile, body.get(), out, seen, false);
                    } else if (include.getNamespace().equals("minecraft") && include.getPath().equals("globals.glsl")) {
                        append(includeFile, read(GLOBALS_FILE), out, seen, false);
                    } else {
                        throw new RuntimeException("shader include not found: " + includeFile + " (from " + file + ")");
                    }
                    continue;
                }
            }
            out.append(line).append('\n');
        }
    }

    private static String read(Identifier file) {
        return tryRead(file).orElseThrow(() -> new RuntimeException("shader not found: " + file));
    }

    private static Optional<String> tryRead(Identifier file) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return Optional.empty();
        try (InputStream in = resource.get().open()) {
            return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("failed to read shader " + file, e);
        }
    }

    private static int compile(int type, String src, Identifier id) {
        int shader = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(shader, src);
        GlStateManager.glCompileShader(shader);
        if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GlStateManager.glGetShaderInfoLog(shader, 4096);
            GlStateManager.glDeleteShader(shader);
            throw new RuntimeException("failed to compile post shader " + id + ": " + log);
        }
        return shader;
    }
}
