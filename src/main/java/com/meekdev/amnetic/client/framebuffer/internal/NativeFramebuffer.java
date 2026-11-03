package com.meekdev.amnetic.client.framebuffer.internal;

import com.meekdev.amnetic.client.framebuffer.*;
import com.meekdev.amnetic.client.render.AmneticResources;
import com.meekdev.amnetic.client.render.NativeScreenProgram;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import java.util.ArrayList;
import java.util.List;

public final class NativeFramebuffer implements AutoCloseable {
    private static final Map<GpuFormat, NativeScreenProgram> BLITS =
            new EnumMap<>(GpuFormat.class);
    static {
        AmneticResources.register(() -> {
            for (var program : BLITS.values()) program.close();
            BLITS.clear();
        });
    }
    private final FramebufferSpec spec;
    private final String name;
    private final List<GpuTexture> colors = new ArrayList<>();
    private final List<GpuTextureView> colorViews = new ArrayList<>();
    private GpuTexture depth;
    private GpuTextureView depthView;
    private int width;
    private int height;

    public NativeFramebuffer(FramebufferSpec spec, String name) {
        this.spec = spec;
        this.name = name == null ? "Amnetic framebuffer" : name;
    }

    public void allocate(int width, int height) {
        if (width <= 0 || height <= 0) throw new FramebufferException("Framebuffer size must be positive");
        if (isAllocated() && this.width == width && this.height == height) return;
        close();
        var device = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST
                | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
        try {
            for (var format : spec.colorFormats()) {
                var texture = device.createTexture(name, usage, format(format), width, height, 1, 1);
                colors.add(texture);
                colorViews.add(device.createTextureView(texture));
            }
            if (spec.depthMode() != DepthMode.NONE) {
                depth = device.createTexture(name + " depth", usage, GpuFormat.D32_FLOAT, width, height, 1, 1);
                depthView = device.createTextureView(depth);
            }
            this.width = width;
            this.height = height;
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    private static GpuFormat format(ColorFormat format) {
        return switch (format) {
            case RGBA8 -> GpuFormat.RGBA8_UNORM;
            case RGBA16F -> GpuFormat.RGBA16_FLOAT;
            case R11G11B10F -> GpuFormat.RG11B10_FLOAT;
            case R8 -> GpuFormat.R8_UNORM;
            case R16F -> GpuFormat.R16_FLOAT;
            case R32F -> GpuFormat.R32_FLOAT;
        };
    }

    public GpuTexture color(int index) { return colors.get(index); }
    public int width() { return width; }
    public int height() { return height; }
    public String name() { return name; }
    public FramebufferSpec spec() { return spec; }
    public boolean isAllocated() { return !colors.isEmpty() || depth != null; }
    public GpuTextureView colorView(int index) { return colorViews.get(index); }
    public GpuTexture depth() { return depth; }
    public GpuTextureView depthView() { return depthView; }

    public static void blitColor(GpuTextureView source, GpuTextureView destination) {
        if (source == destination) return;
        var program = BLITS.computeIfAbsent(destination.texture().getFormat(), format ->
                new NativeScreenProgram(
                        Identifier.fromNamespaceAndPath("amnetic", "shaders/framebuffer/copy.fsh"),
                        new ColorTargetState(Optional.empty(), format, 15)));
        program.setTexture("Source", source);
        program.draw(RenderPassDescriptor.builder(() -> "Amnetic framebuffer resample")
                .withColorAttachment(destination).build());
    }

    public RenderPassDescriptor descriptor() {
        var builder = RenderPassDescriptor.builder(() -> name);
        for (var view : colorViews) builder.withColorAttachment(view);
        if (depthView != null) builder.withDepthAttachment(depthView);
        return builder.build();
    }

    public void clear(float r, float g, float b, float a) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        for (var color : colors) encoder.clearColorTexture(color, new Vector4f(r, g, b, a));
        if (depth != null) encoder.clearDepthTexture(depth, 0.0);
        encoder.submit();
    }

    @Override public void close() {
        for (var view : colorViews) view.close();
        colorViews.clear();
        if (depthView != null) { depthView.close(); depthView = null; }
        for (var color : colors) color.close();
        colors.clear();
        if (depth != null) { depth.close(); depth = null; }
        width = height = 0;
    }
}
