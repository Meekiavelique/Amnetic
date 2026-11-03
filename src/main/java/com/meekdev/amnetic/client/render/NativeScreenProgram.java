package com.meekdev.amnetic.client.render;

import com.meekdev.amnetic.client.post.UniformValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.*;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Pattern;
//? if >=26.3 {
//? } else {
/*import com.meekdev.amnetic.client.render.NativeShaderSource262;
import com.mojang.blaze3d.shaders.ShaderType;
*///?}

public final class NativeScreenProgram implements AutoCloseable {
    private static final Pattern UNIFORM = Pattern.compile("(?m)^\\s*uniform\\s+(\\w+)\\s+(\\w+)(?:\\s*\\[\\s*(\\d+)\\s*])?\\s*;");
    private static final Identifier SCREEN_VERTEX = Identifier.fromNamespaceAndPath("amnetic", "shaders/native/screen.vsh");
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/NativeShaders");
    private static final int BLOCK_ALIGNMENT = 16;
    private static final int UNIFORM_UPLOAD_ALIGNMENT = 256;

    private record Field(String type, int offset, int count, int stride) {}

    private final Identifier sourceId;
    private final String sourceVertex;
    private final Set<String> switches;
    private final UnaryOperator<String> sourceTransform;
    private long revision = ShaderProgram.revision();
    private final Map<String, Field> fields = new LinkedHashMap<>();
    private final Map<String, String> samplerTypes = new LinkedHashMap<>();
    private final Map<String, GpuTextureView> textures = new HashMap<>();
    private final Set<String> nearestTextures = new HashSet<>();
    private final Map<String, GpuBufferSlice> texels = new HashMap<>();
    private ByteBuffer parameters;
    private String fragment;
    private final ColorTargetState[] targets;
    private String vertex;
    private final VertexFormat[] vertexFormats;
    private final boolean cull;
    private final Optional<DepthStencilState> depth;
    //? if >=26.3 {
    private CompiledRenderPipeline pipeline;
    //? } else {
    /*private RenderPipeline pipeline;
    *///? }
    private GpuSampler sampler;
    private GpuSampler shadowSampler;

    public NativeScreenProgram(Identifier fragmentId, ColorTargetState... targets) {
        this(null, fragmentId, new VertexFormat[0], false, Optional.empty(), Set.of(), UnaryOperator.identity(), targets);
    }

    public NativeScreenProgram(Identifier fragmentId, Set<String> switches, ColorTargetState... targets) {
        this(null, fragmentId, new VertexFormat[0], false, Optional.empty(), switches, UnaryOperator.identity(), targets);
    }

    public NativeScreenProgram(Identifier fragmentId, UnaryOperator<String> transform, ColorTargetState... targets) {
        this(null, fragmentId, new VertexFormat[0], false, Optional.empty(), Set.of(), transform, targets);
    }

    public NativeScreenProgram(Identifier vertexId, Identifier fragmentId, VertexFormat[] vertexFormats,
                               boolean cull, Optional<DepthStencilState> depth, ColorTargetState... targets) {
        this(vertexId, fragmentId, vertexFormats, cull, depth, Set.of(), targets);
    }

    public NativeScreenProgram(Identifier vertexId, Identifier fragmentId, VertexFormat[] vertexFormats,
                               boolean cull, Optional<DepthStencilState> depth, Set<String> switches,
                               ColorTargetState... targets) {
        this(ShaderSwitches.apply(ShaderProgram.readSource(vertexId), switches), fragmentId, vertexFormats,
                cull, depth, switches, UnaryOperator.identity(), targets);
    }

    public NativeScreenProgram(String vertexSource, Identifier fragmentId, VertexFormat[] vertexFormats,
                               boolean cull, Optional<DepthStencilState> depth, ColorTargetState... targets) {
        this(vertexSource, fragmentId, vertexFormats, cull, depth, Set.of(), UnaryOperator.identity(), targets);
    }

    public NativeScreenProgram(String vertexSource, Identifier fragmentId, VertexFormat[] vertexFormats,
                               boolean cull, Optional<DepthStencilState> depth, UnaryOperator<String> transform,
                               ColorTargetState... targets) {
        this(vertexSource, fragmentId, vertexFormats, cull, depth, Set.of(), transform, targets);
    }

    private NativeScreenProgram(String vertexSource, Identifier fragmentId, VertexFormat[] vertexFormats,
                                boolean cull, Optional<DepthStencilState> depth, Set<String> switches,
                                UnaryOperator<String> transform, ColorTargetState... targets) {
        sourceId = fragmentId;
        sourceVertex = vertexSource;
        this.switches = switches;
        sourceTransform = transform;
        this.targets = targets.clone();
        this.vertexFormats = vertexFormats.clone();
        this.cull = cull;
        this.depth = depth;
        String raw = transform.apply(ShaderSwitches.apply(ShaderProgram.readSource(fragmentId), switches));
        if (vertexSource != null) raw = withVertexUniforms(raw, vertexSource);
        var declarations = new StringBuilder();
        String body = collectUniforms(raw, declarations)
                .replaceFirst("#version[^\\r\\n]*", "#version 450")
                .replaceAll("(?m)^in vec2 vUV;", "layout(location=0) in vec2 vUV;")
                .replaceAll("(?m)^out vec4 FragColor;", "layout(location=0) out vec4 FragColor;");
        String fragmentBody = withParameterBlock(body, declarations);
        var locations = new LinkedHashMap<String, Integer>();
        locations.put("vUV", 0);
        if (vertexSource == null) {
            vertex = ShaderProgram.readSource(SCREEN_VERTEX);
        } else {
            String rewrittenVertex = vertexSource.replaceFirst("#version[^\\r\\n]*", "#version 450")
                    .replace("gl_VertexID", "gl_VertexIndex")
                    .replace("gl_InstanceID", "gl_InstanceIndex");
            rewrittenVertex = UNIFORM.matcher(rewrittenVertex)
                    .replaceAll(result -> result.group(1).contains("sampler") ? result.group() : "");
            locations.clear();
            vertex = locateVaryings(withParameterBlock(rewrittenVertex, declarations), "out", locations);
        }
        String fragmentOutputs = locateVaryings(fragmentBody, "out", new LinkedHashMap<>());
        fragment = locateVaryings(fragmentOutputs, "in", locations);
    }

    private static String withVertexUniforms(String fragmentSource, String vertexSource) {
        var vertexUniforms = UNIFORM.matcher(vertexSource);
        var missing = new StringBuilder();
        while (vertexUniforms.find()) {
            var declared = Pattern.compile("\\buniform\\s+\\w+\\s+" + Pattern.quote(vertexUniforms.group(2)) + "\\b");
            if (!declared.matcher(fragmentSource).find()) missing.append(vertexUniforms.group()).append('\n');
        }
        int insertion = fragmentSource.indexOf('\n') + 1;
        return fragmentSource.substring(0, insertion) + missing + fragmentSource.substring(insertion);
    }

    private String collectUniforms(String source, StringBuilder declarations) {
        var matcher = UNIFORM.matcher(source);
        var rewritten = new StringBuilder();
        int size = 0;
        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            boolean array = matcher.group(3) != null;
            int count = array ? Integer.parseInt(matcher.group(3)) : 1;
            if (type.contains("sampler")) {
                samplerTypes.put(name, type);
                matcher.appendReplacement(rewritten, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            int alignment = array ? Math.max(BLOCK_ALIGNMENT, alignment(type)) : alignment(type);
            int stride = array ? align(byteSize(type), BLOCK_ALIGNMENT) : byteSize(type);
            size = align(size, alignment);
            if (fields.putIfAbsent(name, new Field(type, size, count, stride)) != null) {
                throw new IllegalArgumentException("Duplicate native uniform " + name);
            }
            declarations.append(type).append(' ').append(name);
            if (array) declarations.append('[').append(count).append(']');
            declarations.append(";\n");
            size += stride * count;
            matcher.appendReplacement(rewritten, "");
        }
        matcher.appendTail(rewritten);
        parameters = BufferUtils.createByteBuffer(Math.max(BLOCK_ALIGNMENT, align(size, BLOCK_ALIGNMENT)));
        return rewritten.toString();
    }

    private int alignment(String type) {
        return switch (type) {
            case "float", "int", "uint", "bool" -> 4;
            case "vec2", "ivec2" -> 8;
            case "vec3", "vec4", "ivec3", "ivec4", "mat4" -> 16;
            default -> throw new IllegalArgumentException("Unsupported native uniform type " + type + " in " + sourceId);
        };
    }

    private static int byteSize(String type) {
        return switch (type) {
            case "float", "int", "uint", "bool" -> 4;
            case "vec2", "ivec2" -> 8;
            case "vec3", "ivec3" -> 12;
            case "mat4" -> 64;
            default -> 16;
        };
    }

    private String withParameterBlock(String source, CharSequence declarations) {
        int insertion = source.indexOf('\n') + 1;
        String members = fields.isEmpty() ? "vec4 Padding;\n" : declarations.toString();
        return source.substring(0, insertion) + "layout(std140) uniform EffectParams {\n" + members + "};\n"
                + source.substring(insertion);
    }

    private static String locateVaryings(String source, String direction, Map<String, Integer> locations) {
        var pattern = Pattern.compile("(?m)^(flat\\s+)?" + direction + "\\s+(\\w+)\\s+(\\w+)\\s*;");
        return pattern.matcher(source).replaceAll(result -> {
            String name = result.group(3);
            int location = locations.computeIfAbsent(name, ignored -> nextLocation(locations));
            if (result.group(2).equals("mat4")) {
                for (int column = 1; column < 4; column++) locations.putIfAbsent(name + "#" + column, location + column);
            }
            String flat = result.group(1) == null ? "" : result.group(1);
            return "layout(location=" + location + ") " + flat + direction + " " + result.group(2) + " " + name + ";";
        });
    }

    private static int nextLocation(Map<String, Integer> locations) {
        return locations.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
    }

    private static int align(int size, int alignment) {
        return (size + alignment - 1) / alignment * alignment;
    }

    private int offset(String name) {
        int bracket = name.indexOf('[');
        String base = bracket < 0 ? name : name.substring(0, bracket);
        Field field = fields.get(base);
        if (field == null) return -1;
        int index = bracket < 0 ? 0 : Integer.parseInt(name.substring(bracket + 1, name.indexOf(']', bracket)));
        if (index < 0 || index >= field.count) throw new IndexOutOfBoundsException(name);
        return field.offset + index * field.stride;
    }

    private int vectorOffset(String name, int count) {
        int bracket = name.indexOf('[');
        Field field = fields.get(bracket < 0 ? name : name.substring(0, bracket));
        if (field == null) return -1;
        if (count * 4 > field.stride) throw new IllegalArgumentException("Uniform size mismatch: " + name);
        return bracket < 0 ? field.offset : offset(name);
    }

    public void setInt(String name, int value) {
        int offset = offset(name);
        if (offset >= 0) parameters.putInt(offset, value);
    }

    public void setFloat(String name, float value) {
        int offset = offset(name);
        if (offset >= 0) parameters.putFloat(offset, value);
    }

    public void setVector(String name, float x, float y) {
        int at = vectorOffset(name, 2);
        if (at < 0) return;
        parameters.putFloat(at, x);
        parameters.putFloat(at + 4, y);
    }

    public void setVector(String name, float x, float y, float z) {
        int at = vectorOffset(name, 3);
        if (at < 0) return;
        parameters.putFloat(at, x);
        parameters.putFloat(at + 4, y);
        parameters.putFloat(at + 8, z);
    }

    public void setVector(String name, float x, float y, float z, float w) {
        int at = vectorOffset(name, 4);
        if (at < 0) return;
        parameters.putFloat(at, x);
        parameters.putFloat(at + 4, y);
        parameters.putFloat(at + 8, z);
        parameters.putFloat(at + 12, w);
    }

    public void setVector(String name, float... values) {
        int offset = offset(name);
        if (offset < 0) return;
        int bracket = name.indexOf('[');
        Field field = fields.get(bracket < 0 ? name : name.substring(0, bracket));
        if (field == null || values.length * 4 > field.stride) throw new IllegalArgumentException("Uniform size mismatch: " + name);
        for (int i = 0; i < values.length; i++) parameters.putFloat(offset + i * 4, values[i]);
    }

    public void setMatrix(String name, Matrix4fc matrix) {
        int offset = offset(name);
        if (offset >= 0) matrix.get(offset, parameters);
    }

    public void setUniformValue(String name, UniformValue value) {
        int at = offset(name);
        if (at >= 0) value.write(parameters, at);
    }

    public void setTexture(String name, GpuTextureView texture) {
        textures.put(name, Objects.requireNonNull(texture));
        nearestTextures.remove(name);
    }

    public void setTextureNearest(String name, GpuTextureView texture) {
        setTexture(name, texture);
        nearestTextures.add(name);
    }

    public void setTexels(String name, GpuBufferSlice buffer) {
        texels.put(name, Objects.requireNonNull(buffer));
    }

    public void draw(RenderPassDescriptor descriptor) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        draw(encoder, descriptor, pass -> pass.draw(3, 1, 0, 0));
        encoder.submit();
    }

    public void draw(CommandEncoder encoder, RenderPassDescriptor descriptor, Consumer<RenderPass> draw) {
        prepare();
        try (var pass = encoder.createRenderPass(descriptor)) {
            drawInPass(encoder, pass, draw);
        }
    }

    public void prepare() {
        refreshSource();
        ensure();
    }

    public void drawInPass(CommandEncoder encoder, RenderPass pass, Consumer<RenderPass> draw) {
        drawBound(encoder, pass, draw, null);
    }

    public final class Batch {
        private final CommandEncoder encoder;
        private final RenderPass pass;
        private final GpuTextureView[] views = new GpuTextureView[samplerTypes.size()];
        private final GpuSampler[] samplers = new GpuSampler[samplerTypes.size()];
        private final GpuBufferSlice[] buffers = new GpuBufferSlice[samplerTypes.size()];

        private Batch(CommandEncoder encoder, RenderPass pass) {
            this.encoder = encoder;
            this.pass = pass;
            pass.setPipeline(pipeline);
        }

        public void draw(Consumer<RenderPass> draw) {
            drawBound(encoder, pass, draw, this);
        }
    }

    public Batch batch(CommandEncoder encoder, RenderPass pass) {
        return new Batch(encoder, pass);
    }

    private void drawBound(CommandEncoder encoder, RenderPass pass, Consumer<RenderPass> draw, Batch batch) {
        parameters.position(0).limit(parameters.capacity());
        var uniform = encoder.transientMemory().uploadGpu(parameters, UNIFORM_UPLOAD_ALIGNMENT, GpuBuffer.USAGE_UNIFORM);
        if (batch == null) pass.setPipeline(pipeline);
        pass.setUniform("EffectParams", uniform);
        int bindingIndex = 0;
        for (var binding : samplerTypes.entrySet()) {
            if (binding.getValue().endsWith("samplerBuffer")) {
                bindTexels(pass, binding.getKey(), bindingIndex, batch);
            } else {
                bindTexture(pass, binding.getKey(), bindingIndex, batch);
            }
            bindingIndex++;
        }
        draw.accept(pass);
    }

    private void bindTexels(RenderPass pass, String name, int bindingIndex, Batch batch) {
        var buffer = texels.get(name);
        if (buffer == null) throw new IllegalStateException("Missing native buffer " + name);
        if (batch != null && buffer.equals(batch.buffers[bindingIndex])) return;
        pass.setUniform(name, buffer);
        if (batch != null) batch.buffers[bindingIndex] = buffer;
    }

    private void bindTexture(RenderPass pass, String name, int bindingIndex, Batch batch) {
        var texture = textures.get(name);
        if (texture == null || texture.isClosed()) throw new IllegalStateException("Missing native texture " + name);
        //? if >=26.3 {
        var chosenSampler = isDepthTexture(name) || nearestTextures.contains(name) ? shadowSampler : sampler;
        if (batch != null && batch.views[bindingIndex] == texture && batch.samplers[bindingIndex] == chosenSampler) return;
        pass.setUniform(name, texture, chosenSampler);
        if (batch != null) {
            batch.views[bindingIndex] = texture;
            batch.samplers[bindingIndex] = chosenSampler;
        }
        //? } else {
        /*pass.bindTexture(name, texture, isDepthTexture(name) ? shadowSampler : sampler);
        *///? }
    }

    private void refreshSource() {
        long current = ShaderProgram.revision();
        if (revision == current) return;
        revision = current;
        NativeScreenProgram replacement = null;
        try {
            replacement = new NativeScreenProgram(sourceVertex, sourceId, vertexFormats, cull, depth,
                    switches, sourceTransform, targets);
            replacement.ensure();
            copyParametersInto(replacement);
            closeGpuResources();
            fields.clear();
            fields.putAll(replacement.fields);
            samplerTypes.clear();
            samplerTypes.putAll(replacement.samplerTypes);
            parameters = replacement.parameters;
            fragment = replacement.fragment;
            vertex = replacement.vertex;
            pipeline = replacement.pipeline;
            sampler = replacement.sampler;
            shadowSampler = replacement.shadowSampler;
            replacement.pipeline = null;
            replacement.sampler = null;
            replacement.shadowSampler = null;
            LOG.info("Reloaded native shader {}", sourceId);
        } catch (RuntimeException failure) {
            if (replacement != null) replacement.close();
            LOG.warn("Could not reload native shader {}; keeping previous pipeline", sourceId, failure);
        }
    }

    private void copyParametersInto(NativeScreenProgram replacement) {
        for (var entry : fields.entrySet()) {
            Field old = entry.getValue();
            Field next = replacement.fields.get(entry.getKey());
            if (next == null || !old.type.equals(next.type)) continue;
            int bytes = Math.min(old.stride, next.stride);
            for (int element = 0; element < Math.min(old.count, next.count); element++) {
                for (int b = 0; b < bytes; b++) {
                    byte value = parameters.get(old.offset + element * old.stride + b);
                    replacement.parameters.put(next.offset + element * next.stride + b, value);
                }
            }
        }
    }

    private static boolean isDepthTexture(String name) {
        return name.equals("DepthSampler") || name.contains("Shadow") && !name.contains("Color");
    }

    private static GpuFormat texelFormat(String samplerType) {
        return switch (samplerType) {
            case "isamplerBuffer" -> GpuFormat.R32_SINT;
            case "usamplerBuffer" -> GpuFormat.R32_UINT;
            default -> GpuFormat.RGBA32_FLOAT;
        };
    }

    private void ensure() {
        if (pipeline != null) return;
        var bindings = BindGroupLayout.builder().withUniform("EffectParams", UniformType.UNIFORM_BUFFER);
        for (var binding : samplerTypes.entrySet()) {
            String name = binding.getKey();
            String type = binding.getValue();
            if (type.endsWith("samplerBuffer")) {
                bindings.withUniform(name, UniformType.TEXEL_BUFFER, texelFormat(type));
                continue;
            }
            //? if >=26.3 {
            bindings.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
            //? } else {
            /*bindings.withSampler(name);
            *///? }
        }
        var builder = RenderPipeline.builder()
                .withLocation(sourceId)
                .withVertexShader(sourceId)
                .withFragmentShader(sourceId)
                .withBindGroupLayout(bindings.build())
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(cull)
                .withDepthStencilState(depth);
        for (int i = 0; i < vertexFormats.length; i++) builder.withVertexBinding(i, vertexFormats[i]);
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] != null) builder.withColorTargetState(i, targets[i]);
        }
        //? if >=26.3 {
        try (ShaderSource sources = new ShaderSource() {
            @Override public String getShader(Identifier id, ShaderType type) {
                return type == ShaderType.FRAGMENT ? fragment : vertex;
            }
            @Override public CachedIncludeSource getInclude(Identifier id) { return null; }
            @Override public void close() {}
        }) {
            pipeline = RenderSystem.getDevice().compilePipeline(builder.build(), sources, Runnable::run).join().finishCompile();
        }
        //? } else {
        /*pipeline = builder.build();
        var compiled = RenderSystem.getDevice().precompilePipeline(pipeline,
                (id, type) -> NativeShaderSource262.adapt(type == ShaderType.FRAGMENT ? fragment : vertex));
        if (!compiled.isValid()) throw new IllegalStateException("Could not compile native shader " + sourceId);
        *///? }
        if (pipeline == null) throw new IllegalStateException("Could not compile native shader " + sourceId);
        try {
            sampler = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
            shadowSampler = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    @Override public void close() {
        textures.clear();
        nearestTextures.clear();
        texels.clear();
        closeGpuResources();
    }

    private void closeGpuResources() {
        if (sampler != null) {
            sampler.close();
            sampler = null;
        }
        if (shadowSampler != null) {
            shadowSampler.close();
            shadowSampler = null;
        }
        if (pipeline != null) {
            //? if >=26.3 {
            pipeline.close();
            //? }
            pipeline = null;
        }
    }
}
