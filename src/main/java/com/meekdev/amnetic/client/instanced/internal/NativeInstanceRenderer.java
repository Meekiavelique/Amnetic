package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.meekdev.amnetic.client.instanced.*;
import com.meekdev.amnetic.client.render.NativeScreenProgram;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Pattern;

final class NativeInstanceRenderer implements AutoCloseable {
    private final InstancedMesh<?> mesh;
    private NativeScreenProgram program;
    private NativeScreenProgram emissiveProgram;
    static Framebuffer emissiveTarget;
    private GpuBuffer geometryBuffer;
    private GpuBuffer indexBuffer;
    private int geometryVersion = -1;

    NativeInstanceRenderer(InstancedMesh<?> mesh) { this.mesh = mesh; }

    void render(InstanceRenderContext context, ByteBuffer instances, int count) {
        if (count == 0 || mesh.geometry().vertexCount() == 0) return;
        ensure();
        NativeScreenProgram program = emissiveTarget == null ? this.program : emissiveProgram;
        if (emissiveTarget != null) program.setFloat("AmneticEmissionStrength", mesh.emissiveStrength());
        program.setMatrix("ProjViewMatrix", new Matrix4f(context.projectionMatrix()).mul(context.viewMatrix()));
        program.setMatrix("ProjectionMatrix", context.projectionMatrix());
        program.setMatrix("ViewMatrix", context.viewMatrix());
        program.setFloat("Time", context.gameTime());
        var camera = context.cameraPos();
        program.setVector("CameraPos", (float) camera.x, (float) camera.y, (float) camera.z);
        program.setInt("WorldSpace", mesh.worldSpace() ? 1 : 0);
        var sun = InstanceMeshEntry.sunDirection();
        program.setVector("SunDir", sun.x, sun.y, sun.z);
        for (var uniform : mesh.customUniforms()) {
            var values = uniform.value().get();
            if (values == null) continue;
            if (values.length == 1) program.setFloat(uniform.name(), values[0]);
            else if (values.length == 16) program.setMatrix(uniform.name(), new Matrix4f().set(values));
            else program.setVector(uniform.name(), values);
        }
        if (mesh.textureId() != null) program.setTexture("TextureSampler", texture(mesh.textureId(), false));
        for (var sampler : mesh.extraSamplers()) {
            if (sampler.nativeTexture() != null) {
                program.setTexture(sampler.uniformName(), sampler.nativeTexture().get());
                continue;
            }
            if (sampler.textureId() == null) throw new IllegalArgumentException(
                    "Raw GL sampler " + sampler.uniformName() + " requires a native texture binding on 26.3");
            program.setTexture(sampler.uniformName(), texture(sampler.textureId(), sampler.fileBacked()));
        }
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var descriptor = RenderPassDescriptor.builder(() -> "Amnetic instanced mesh")
                .withColorAttachment(target.getColorTextureView());
        if (mesh.renderState().depthTest() || mesh.renderState().depthWrite()) descriptor.withDepthAttachment(target.getDepthTextureView());
        var renderTarget = emissiveTarget == null ? descriptor.build() : emissiveTarget.renderPassDescriptor();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        ensureGeometry();
        var payload = encoder.transientMemory().uploadGpu(instances, 4, GpuBuffer.USAGE_VERTEX);
        program.draw(encoder, renderTarget, pass -> {
            pass.setVertexBuffer(0, geometryBuffer.slice());
            pass.setVertexBuffer(1, payload);
            if (indexBuffer != null) {
                pass.setIndexBuffer(indexBuffer, IndexType.INT);
                pass.drawIndexed(mesh.geometry().indexCount(), count, 0, 0, 0);
            } else pass.draw(mesh.geometry().vertexCount(), count, 0, 0);
        });
        encoder.submit();
    }

    private void ensureGeometry() {
        if (geometryBuffer != null && geometryVersion == mesh.geometry().version()) return;
        if (geometryBuffer != null) { geometryBuffer.close(); geometryBuffer = null; }
        if (indexBuffer != null) { indexBuffer.close(); indexBuffer = null; }
        var device = RenderSystem.getDevice();
        try {
            var floats = mesh.geometry().verticesAsBuffer();
            var vertices = BufferUtils.createByteBuffer(floats.remaining() * Float.BYTES);
            vertices.asFloatBuffer().put(floats);
            geometryBuffer = device.createBuffer(() -> "Amnetic mesh geometry", GpuBuffer.USAGE_VERTEX, vertices);
            if (mesh.geometry().hasIndices()) {
                var ints = mesh.geometry().indicesAsBuffer();
                var indices = BufferUtils.createByteBuffer(ints.remaining() * Integer.BYTES);
                indices.asIntBuffer().put(ints);
                indexBuffer = device.createBuffer(() -> "Amnetic mesh indices", GpuBuffer.USAGE_INDEX, indices);
            }
            geometryVersion = mesh.geometry().version();
        } catch (RuntimeException failure) { close(); throw failure; }
    }

    private GpuTextureView texture(Identifier id, boolean imported) {
        var manager = Minecraft.getInstance().getTextureManager();
        var texture = manager.getTexture(id);
        if (texture == null || texture.getTextureView() == null) throw new IllegalStateException("Native texture not available: " + id);
        return texture.getTextureView();
    }

    private void ensure() {
        if (program != null) return;
        Identifier vertexId, fragmentId;
        if (mesh.vertexShaderId() != null) {
            vertexId = path(mesh.vertexShaderId(), ".vsh"); fragmentId = path(mesh.fragmentShaderId(), ".fsh");
        } else if (mesh.isBuiltin()) {
            String name = "instance/" + mesh.builtinShader().shaderId() + (mesh.flatShaded() ? "_flat" : "");
            vertexId = path(Identifier.fromNamespaceAndPath("amnetic", name), ".vsh");
            fragmentId = path(Identifier.fromNamespaceAndPath("amnetic", name), ".fsh");
        } else { vertexId = path(mesh.customShaderId(), ".vsh"); fragmentId = path(mesh.customShaderId(), ".fsh"); }
        String vertex = ShaderProgram.readSource(vertexId);
        InstanceLayout geometry = mesh.vertexLayout();
        if (geometry == null) {
            var builder = InstanceLayout.perVertex().vec3(0);
            if (mesh.geometry().hasTextureCoordinates()) builder.vec2(1);
            geometry = builder.build();
        }
        var state = mesh.renderState();
        Optional<BlendFunction> blend = switch (state.blendMode()) {
            case NONE -> Optional.empty(); case ALPHA -> Optional.of(BlendFunction.TRANSLUCENT);
            case ADDITIVE -> Optional.of(BlendFunction.LIGHTNING);
            case MULTIPLY -> Optional.of(new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ZERO));
        };
        Optional<DepthStencilState> depth = state.depthTest() || state.depthWrite()
                ? Optional.of(new DepthStencilState(state.depthTest() ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.ALWAYS_PASS, state.depthWrite()))
                : Optional.empty();
        var formats = new VertexFormat[]{format(vertex, geometry, mesh.geometry().vertexStrideBytes()), format(vertex, mesh.layout(), mesh.layout().stride())};
        program = new NativeScreenProgram(vertex, fragmentId,
                formats,
                state.backfaceCulling(), depth, new ColorTargetState(blend, GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL));
        emissiveProgram = new NativeScreenProgram(vertex, fragmentId, formats, state.backfaceCulling(),
                Optional.of(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)),
                NativeInstanceRenderer::emissiveFragment,
                new ColorTargetState(Optional.of(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE)), GpuFormat.RG11B10_FLOAT, ColorTargetState.WRITE_ALL));
    }

    private static String emissiveFragment(String source) {
        var output = Pattern.compile("(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+vec4\\s+(\\w+)\\s*;").matcher(source);
        if (!output.find()) throw new IllegalArgumentException("Emissive mesh shader requires a vec4 color output");
        String color = output.group(1);
        int insertion = source.indexOf('\n') + 1;
        source = source.substring(0, insertion) + "uniform float AmneticEmissionStrength;\n" + source.substring(insertion);
        source = source.replaceFirst("\\bvoid\\s+main\\s*\\(", "void amneticOriginalMain(");
        return source + "\nvoid main() { amneticOriginalMain(); " + color + ".rgb *= clamp(" + color
                + ".a, 0.0, 1.0) * AmneticEmissionStrength; }\n";
    }

    private static Identifier path(Identifier id, String extension) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "shaders/" + id.getPath() + extension);
    }

    private static VertexFormat format(String vertex, InstanceLayout layout, int stride) {
        var builder = VertexFormat.builder(layout.divisor());
        int lastOffset = layout.attributes().stream().mapToInt(a -> a.byteOffset()).max().orElse(0);
        var inputs = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*in\\s+(\\w+)\\s+(\\w+)").matcher(vertex);
        while (inputs.find()) {
            int location = Integer.parseInt(inputs.group(1));
            var attribute = layout.attributes().stream().filter(a -> a.location() == location).findFirst();
            if (attribute.isEmpty()) continue;
            var spec = attribute.get();
            GpuFormat format = switch (spec.components()) {
                case 1 -> GpuFormat.R32_FLOAT; case 2 -> GpuFormat.RG32_FLOAT;
                case 3 -> GpuFormat.RGB32_FLOAT; case 4 -> GpuFormat.RGBA32_FLOAT;
                default -> throw new IllegalArgumentException("Invalid attribute width");
            };
            int attributeStride = spec.components() * 4;
            //? if <26.3 {
            /*if (spec.byteOffset() == lastOffset && !inputs.group(2).equals("mat4")) attributeStride = stride - spec.byteOffset();
            *///?}
            builder.addAttribute(inputs.group(3), spec.byteOffset(), attributeStride, format,
                    inputs.group(2).equals("mat4") ? 4 : 1);
        }
        //? if >=26.3 {
        builder.addAttribute("__AmneticStride", stride - 4, 4, GpuFormat.R32_FLOAT, 1);
        //?}
        return builder.build();
    }

    void invalidateShader() { if (program != null) { program.close(); program = null; } if(emissiveProgram != null) { emissiveProgram.close(); emissiveProgram = null; } }

    @Override public void close() {
        invalidateShader();
        if (geometryBuffer != null) { geometryBuffer.close(); geometryBuffer = null; }
        if (indexBuffer != null) { indexBuffer.close(); indexBuffer = null; }
    }
}
