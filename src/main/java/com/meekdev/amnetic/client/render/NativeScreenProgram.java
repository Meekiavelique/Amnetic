package com.meekdev.amnetic.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.*;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Pattern;

/** Fullscreen shaders with explicit std140 parameters and native texture bindings. */
public final class NativeScreenProgram implements AutoCloseable {
    private static final Pattern UNIFORM = Pattern.compile("(?m)^\\s*uniform\\s+(\\w+)\\s+(\\w+)(?:\\s*\\[\\s*(\\d+)\\s*])?\\s*;");
    private record Field(String type, int offset, int count, int stride) {}
    private final Identifier sourceId;
    private final String sourceVertex;
    private final java.util.function.UnaryOperator<String> sourceTransform;
    private long revision = ShaderProgram.revision();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Amnetic/NativeShaders");
    private final Map<String, Field> fields = new LinkedHashMap<>();
    private final Map<String, String> samplerTypes = new LinkedHashMap<>();
    private final Map<String, GpuTextureView> textures = new HashMap<>();
    private final Set<String> nearestTextures = new HashSet<>();
    private final Map<String, GpuBufferSlice> texels = new HashMap<>();
    private ByteBuffer parameters;
    private String fragment;
    private final ColorTargetState[] targets;
    private String vertex;
    private final com.mojang.renderpearl.api.vertex.VertexFormat[] vertexFormats;
    private final boolean cull;
    private final Optional<DepthStencilState> depth;
    //? if >=26.3 {
    private CompiledRenderPipeline pipeline;
    //? } else {
    /*private RenderPipeline pipeline;
    *///? }
    private GpuSampler sampler, shadowSampler;

    public NativeScreenProgram(Identifier fragmentId, ColorTargetState... targets) {
        this(null, fragmentId, new com.mojang.renderpearl.api.vertex.VertexFormat[0], false, Optional.empty(), targets);
    }

    public NativeScreenProgram(Identifier fragmentId, java.util.function.UnaryOperator<String> transform, ColorTargetState... targets) {
        this(null, fragmentId, new com.mojang.renderpearl.api.vertex.VertexFormat[0], false, Optional.empty(), transform, targets);
    }

    public NativeScreenProgram(String vertexSource, Identifier fragmentId,
                               com.mojang.renderpearl.api.vertex.VertexFormat[] vertexFormats,
                               boolean cull, Optional<DepthStencilState> depth, ColorTargetState... targets) {
        this(vertexSource, fragmentId, vertexFormats, cull, depth, java.util.function.UnaryOperator.identity(), targets);
    }

    public NativeScreenProgram(String vertexSource, Identifier fragmentId,
                               com.mojang.renderpearl.api.vertex.VertexFormat[] vertexFormats,
                               boolean cull, Optional<DepthStencilState> depth,
                               java.util.function.UnaryOperator<String> transform, ColorTargetState... targets) {
        sourceId = fragmentId;
        sourceVertex = vertexSource;
        sourceTransform = transform;
        this.targets = targets.clone();
        this.vertexFormats = vertexFormats.clone();
        this.cull = cull;
        this.depth = depth;
        String raw = transform.apply(ShaderProgram.readSource(fragmentId));
        if (vertexSource != null) {
            var vertexUniforms = UNIFORM.matcher(vertexSource);
            var missing = new StringBuilder();
            while (vertexUniforms.find()) {
                if (!Pattern.compile("\\buniform\\s+\\w+\\s+" + Pattern.quote(vertexUniforms.group(2)) + "\\b").matcher(raw).find()) {
                    missing.append(vertexUniforms.group()).append('\n');
                }
            }
            int insertion = raw.indexOf('\n') + 1;
            raw = raw.substring(0, insertion) + missing + raw.substring(insertion);
        }
        var matcher = UNIFORM.matcher(raw);
        var declarations = new StringBuilder();
        var rewritten = new StringBuilder();
        int size = 0;
        while (matcher.find()) {
            String type = matcher.group(1), name = matcher.group(2);
            int count = matcher.group(3) == null ? 1 : Integer.parseInt(matcher.group(3));
            if (type.contains("sampler")) {
                samplerTypes.put(name, type);
                matcher.appendReplacement(rewritten, java.util.regex.Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            int alignment = switch (type) {
                case "float", "int", "uint", "bool" -> 4;
                case "vec2", "ivec2" -> 8;
                case "vec3", "vec4", "ivec3", "ivec4", "mat4" -> 16;
                default -> throw new IllegalArgumentException("Unsupported native uniform type " + type + " in " + fragmentId);
            };
            int bytes = switch (type) {
                case "float", "int", "uint", "bool" -> 4;
                case "vec2", "ivec2" -> 8;
                case "vec3", "ivec3" -> 12;
                case "mat4" -> 64;
                default -> 16;
            };
            boolean array = matcher.group(3) != null;
            if (array) alignment = Math.max(16, alignment);
            int stride = array ? align(bytes, 16) : bytes;
            size = align(size, alignment);
            if (fields.putIfAbsent(name, new Field(type, size, count, stride)) != null)
                throw new IllegalArgumentException("Duplicate native uniform " + name);
            declarations.append(type).append(' ').append(name);
            if (array) declarations.append('[').append(count).append(']');
            declarations.append(";\n");
            size += stride * count;
            matcher.appendReplacement(rewritten, "");
        }
        matcher.appendTail(rewritten);
        parameters = BufferUtils.createByteBuffer(Math.max(16, align(size, 16)));
        String body = rewritten.toString().replaceFirst("#version[^\\r\\n]*", "#version 450")
                .replaceAll("(?m)^in vec2 vUV;", "layout(location=0) in vec2 vUV;")
                .replaceAll("(?m)^out vec4 FragColor;", "layout(location=0) out vec4 FragColor;");
        int insertion = body.indexOf('\n') + 1;
        String fragmentBody = body.substring(0, insertion) + "layout(std140) uniform EffectParams {\n"
                + (fields.isEmpty() ? "vec4 Padding;\n" : declarations) + "};\n" + body.substring(insertion);
        var locations = new LinkedHashMap<String, Integer>();
        locations.put("vUV", 0);
        if (vertexSource == null) vertex = """
                #version 450
                layout(location=0) out vec2 vUV;
                void main() {
                    vec2 p = vec2(float((gl_VertexIndex << 1) & 2), float(gl_VertexIndex & 2));
                    vUV = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                }
                """;
        else {
            String rewrittenVertex = vertexSource.replaceFirst("#version[^\\r\\n]*", "#version 450")
                    .replace("gl_VertexID", "gl_VertexIndex").replace("gl_InstanceID", "gl_InstanceIndex");
            rewrittenVertex = UNIFORM.matcher(rewrittenVertex).replaceAll(result ->
                    result.group(1).contains("sampler") ? result.group() : "");
            int at = rewrittenVertex.indexOf('\n') + 1;
            locations.clear();
            String vertexBody = rewrittenVertex.substring(0, at) + "layout(std140) uniform EffectParams {\n"
                    + (fields.isEmpty() ? "vec4 Padding;\n" : declarations) + "};\n" + rewrittenVertex.substring(at);
            vertex = locateVaryings(vertexBody, "out", locations);
        }
        fragment = locateVaryings(locateVaryings(fragmentBody, "out", new LinkedHashMap<>()), "in", locations);
    }

    private static String locateVaryings(String source, String direction, Map<String, Integer> locations) {
        var pattern = Pattern.compile("(?m)^(flat\\s+)?" + direction + "\\s+(\\w+)\\s+(\\w+)\\s*;");
        return pattern.matcher(source).replaceAll(result -> {
            String name = result.group(3);
            int location = locations.computeIfAbsent(name, ignored -> locations.values().stream()
                    .mapToInt(Integer::intValue).max().orElse(-1) + 1);
            if (result.group(2).equals("mat4")) {
                // Matrix varyings occupy one location per column.
                for (int column = 1; column < 4; column++) locations.putIfAbsent(name + "#" + column, location + column);
            }
            return "layout(location=" + location + ") " + (result.group(1) == null ? "" : result.group(1))
                    + direction + " " + result.group(2) + " " + name + ";";
        });
    }

    private static int align(int size, int alignment) { return (size + alignment - 1) / alignment * alignment; }
    private int offset(String name) {
        int bracket = name.indexOf('[');
        String base = bracket < 0 ? name : name.substring(0, bracket);
        Field field = fields.get(base);
        if (field == null) return -1;
        int index = bracket < 0 ? 0 : Integer.parseInt(name.substring(bracket + 1, name.indexOf(']', bracket)));
        if (index < 0 || index >= field.count) throw new IndexOutOfBoundsException(name);
        return field.offset + index * field.stride;
    }
    public void setInt(String name, int value) { int offset = offset(name); if (offset >= 0) parameters.putInt(offset, value); }
    public void setFloat(String name, float value) { int offset = offset(name); if (offset >= 0) parameters.putFloat(offset, value); }
    private int vectorOffset(String name,int count) {
        int bracket=name.indexOf('[');Field field=fields.get(bracket<0?name:name.substring(0,bracket));
        if(field==null)return -1;
        if(count*4>field.stride)throw new IllegalArgumentException("Uniform size mismatch: "+name);
        return bracket<0?field.offset:offset(name);
    }
    public void setVector(String name,float x,float y){int at=vectorOffset(name,2);if(at>=0){parameters.putFloat(at,x);parameters.putFloat(at+4,y);}}
    public void setVector(String name,float x,float y,float z){int at=vectorOffset(name,3);if(at>=0){parameters.putFloat(at,x);parameters.putFloat(at+4,y);parameters.putFloat(at+8,z);}}
    public void setVector(String name,float x,float y,float z,float w){int at=vectorOffset(name,4);if(at>=0){parameters.putFloat(at,x);parameters.putFloat(at+4,y);parameters.putFloat(at+8,z);parameters.putFloat(at+12,w);}}
    public void setVector(String name, float... values) {
        int offset = offset(name); if (offset < 0) return;
        int bracket = name.indexOf('[');
        Field field = fields.get(bracket < 0 ? name : name.substring(0, bracket));
        if (field == null || values.length * 4 > field.stride) throw new IllegalArgumentException("Uniform size mismatch: " + name);
        for (int i = 0; i < values.length; i++) parameters.putFloat(offset + i * 4, values[i]);
    }
    public void setMatrix(String name, Matrix4fc matrix) { int offset = offset(name); if (offset >= 0) matrix.get(offset, parameters); }
    public void setUniformValue(String name, com.meekdev.amnetic.client.post.UniformValue value) { int at = offset(name); if(at >= 0) value.write(parameters, at); }
    public void setTexture(String name, GpuTextureView texture) { textures.put(name, Objects.requireNonNull(texture));nearestTextures.remove(name); }
    public void setTextureNearest(String name,GpuTextureView texture) {setTexture(name,texture);nearestTextures.add(name);}
    public void setTexels(String name, GpuBufferSlice buffer) { texels.put(name, Objects.requireNonNull(buffer)); }

    public void draw(RenderPassDescriptor descriptor) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        draw(encoder, descriptor, pass -> pass.draw(3, 1, 0, 0));
        encoder.submit();
    }

    public void draw(com.mojang.renderpearl.api.commands.CommandEncoder encoder, RenderPassDescriptor descriptor,
                     java.util.function.Consumer<com.mojang.renderpearl.api.commands.RenderPass> draw) {
        prepare();
        try (var pass = encoder.createRenderPass(descriptor)) {
            drawInPass(encoder,pass,draw);
        }
    }

    public void prepare() {
        refreshSource();
        ensure();
    }


    public void drawInPass(com.mojang.renderpearl.api.commands.CommandEncoder encoder,
                           com.mojang.renderpearl.api.commands.RenderPass pass,
                           java.util.function.Consumer<com.mojang.renderpearl.api.commands.RenderPass> draw) {
        drawBound(encoder,pass,draw,null);
    }

    public final class Batch {
        private final com.mojang.renderpearl.api.commands.CommandEncoder encoder;
        private final com.mojang.renderpearl.api.commands.RenderPass pass;
        private final GpuTextureView[] views=new GpuTextureView[samplerTypes.size()];
        private final GpuSampler[] samplers=new GpuSampler[samplerTypes.size()];
        private final GpuBufferSlice[] buffers=new GpuBufferSlice[samplerTypes.size()];
        private Batch(com.mojang.renderpearl.api.commands.CommandEncoder encoder,com.mojang.renderpearl.api.commands.RenderPass pass){this.encoder=encoder;this.pass=pass;pass.setPipeline(pipeline);}
        public void draw(java.util.function.Consumer<com.mojang.renderpearl.api.commands.RenderPass> draw){drawBound(encoder,pass,draw,this);}
    }
    public Batch batch(com.mojang.renderpearl.api.commands.CommandEncoder encoder,com.mojang.renderpearl.api.commands.RenderPass pass){return new Batch(encoder,pass);}

    private void drawBound(com.mojang.renderpearl.api.commands.CommandEncoder encoder,
                           com.mojang.renderpearl.api.commands.RenderPass pass,
                           java.util.function.Consumer<com.mojang.renderpearl.api.commands.RenderPass> draw,Batch batch) {
        parameters.position(0).limit(parameters.capacity());
        var uniform = encoder.transientMemory().uploadGpu(parameters, 256, GpuBuffer.USAGE_UNIFORM);
            if(batch==null)pass.setPipeline(pipeline);
            pass.setUniform("EffectParams", uniform);
            int bindingIndex=0;
            for (var binding : samplerTypes.entrySet()) {
                if (binding.getValue().endsWith("samplerBuffer")) {
                    var buffer = texels.get(binding.getKey());
                    if (buffer == null) throw new IllegalStateException("Missing native buffer " + binding.getKey());
                    if(batch==null||!buffer.equals(batch.buffers[bindingIndex])) {
                        pass.setUniform(binding.getKey(), buffer);if(batch!=null)batch.buffers[bindingIndex]=buffer;
                    }
                } else {
                    var texture = textures.get(binding.getKey());
                    if (texture == null || texture.isClosed()) throw new IllegalStateException("Missing native texture " + binding.getKey());
                    //? if >=26.3 {
            var chosenSampler=isDepthTexture(binding.getKey()) || nearestTextures.contains(binding.getKey()) ? shadowSampler : sampler;
            if(batch==null||batch.views[bindingIndex]!=texture||batch.samplers[bindingIndex]!=chosenSampler) {
                pass.setUniform(binding.getKey(), texture,chosenSampler);
                if(batch!=null){batch.views[bindingIndex]=texture;batch.samplers[bindingIndex]=chosenSampler;}
            }
            //? } else {
            /*pass.bindTexture(binding.getKey(), texture, isDepthTexture(binding.getKey()) ? shadowSampler : sampler);
            *///? }
                }
                bindingIndex++;
            }
            draw.accept(pass);
    }

    /** Compile a replacement first: a broken edit leaves the last valid pipeline usable. */
    private void refreshSource() {
        long current = ShaderProgram.revision();
        if (revision == current) return;
        revision = current;
        NativeScreenProgram replacement = null;
        try {
            replacement = new NativeScreenProgram(sourceVertex, sourceId, vertexFormats, cull, depth, sourceTransform, targets);
            replacement.ensure();
            for (var entry : fields.entrySet()) {
                Field old = entry.getValue(), next = replacement.fields.get(entry.getKey());
                if (next == null || !old.type.equals(next.type)) continue;
                for (int element = 0; element < Math.min(old.count, next.count); element++) {
                    int bytes = Math.min(old.stride, next.stride);
                    for (int b = 0; b < bytes; b++) replacement.parameters.put(next.offset + element * next.stride + b,
                            parameters.get(old.offset + element * old.stride + b));
                }
            }
            closeGpuResources();
            fields.clear(); fields.putAll(replacement.fields);
            samplerTypes.clear(); samplerTypes.putAll(replacement.samplerTypes);
            parameters = replacement.parameters; fragment = replacement.fragment; vertex = replacement.vertex;
            pipeline = replacement.pipeline; sampler = replacement.sampler; shadowSampler = replacement.shadowSampler;
            replacement.pipeline = null; replacement.sampler = null; replacement.shadowSampler = null;
            LOG.info("Reloaded native shader {}", sourceId);
        } catch (RuntimeException failure) {
            if (replacement != null) replacement.close();
            LOG.warn("Could not reload native shader {}; keeping previous pipeline", sourceId, failure);
        }
    }

    private static boolean isDepthTexture(String name) {
        return name.equals("DepthSampler") || name.contains("Shadow") && !name.contains("Color");
    }

    private void ensure() {
        if (pipeline != null) return;
        var bindings = BindGroupLayout.builder().withUniform("EffectParams", UniformType.UNIFORM_BUFFER);
        for (var binding : samplerTypes.entrySet()) {
            if (binding.getValue().endsWith("samplerBuffer")) bindings.withUniform(binding.getKey(), UniformType.TEXEL_BUFFER,
                    binding.getValue().equals("isamplerBuffer")?com.mojang.renderpearl.api.GpuFormat.R32_SINT:binding.getValue().equals("usamplerBuffer")?com.mojang.renderpearl.api.GpuFormat.R32_UINT:com.mojang.renderpearl.api.GpuFormat.RGBA32_FLOAT);
            //? if >=26.3 {
            else bindings.withUniform(binding.getKey(), UniformType.COMBINED_IMAGE_SAMPLER);
            //? } else {
            /*else bindings.withSampler(binding.getKey());
            *///? }
        }
        var builder = RenderPipeline.builder().withLocation(sourceId).withVertexShader(sourceId).withFragmentShader(sourceId)
                .withBindGroupLayout(bindings.build()).withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(cull).withDepthStencilState(depth);
        for (int i = 0; i < vertexFormats.length; i++) builder.withVertexBinding(i, vertexFormats[i]);
        for (int i = 0; i < targets.length; i++) if (targets[i] != null) builder.withColorTargetState(i, targets[i]);
        //? if >=26.3 {
        try (ShaderSource sources = new ShaderSource() {
            @Override public String getShader(Identifier id, ShaderType type) {
                return type == ShaderType.FRAGMENT ? fragment : vertex;
            }
            @Override public CachedIncludeSource getInclude(Identifier id) { return null; }
            @Override public void close() {}
        }) { pipeline = RenderSystem.getDevice().compilePipeline(builder.build(), sources, Runnable::run).join().finishCompile(); }
        //? } else {
        /*pipeline=builder.build();
        if(!RenderSystem.getDevice().precompilePipeline(pipeline,(id,type)->com.meekdev.amnetic.client.render.NativeShaderSource262.adapt(type==com.mojang.blaze3d.shaders.ShaderType.FRAGMENT?fragment:vertex)).isValid())throw new IllegalStateException("Could not compile native shader "+sourceId);
        *///? }
        if (pipeline == null) throw new IllegalStateException("Could not compile native shader " + sourceId);
        try {
            sampler = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
            shadowSampler = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                        FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
        } catch (RuntimeException failure) { close(); throw failure; }
    }
    @Override public void close() {
        textures.clear();nearestTextures.clear();texels.clear();
        closeGpuResources();
    }
    private void closeGpuResources() {
        if (sampler != null) { sampler.close(); sampler = null; }
        if (shadowSampler != null) { shadowSampler.close(); shadowSampler = null; }
        if (pipeline != null) {
            //? if >=26.3 {
            pipeline.close();
            //? }
            pipeline = null; }
    }
}
