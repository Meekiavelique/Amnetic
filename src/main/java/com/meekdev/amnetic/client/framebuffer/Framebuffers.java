package com.meekdev.amnetic.client.framebuffer;

public final class Framebuffers {

    private Framebuffers() {}

    public static Framebuffer fixed(int width, int height, FramebufferSpec spec) {
        return Framebuffer.createFixed(width, height, spec);
    }

    public static Framebuffer fixed(String name, int width, int height, FramebufferSpec spec) {
        return Framebuffer.createFixed(name, width, height, spec);
    }

    public static Framebuffer screen(FramebufferSpec spec) {
        return Framebuffer.createScreen(1f, spec);
    }

    public static Framebuffer screen(String name, FramebufferSpec spec) {
        return Framebuffer.createScreen(name, 1f, spec);
    }

    public static Framebuffer screen(String name, float scale, FramebufferSpec spec) {
        if (scale <= 0f) {
            throw new FramebufferException("screen scale must be > 0, got " + scale);
        }
        return Framebuffer.createScreen(name, scale, spec);
    }

    public static Framebuffer screen(float scale, FramebufferSpec spec) {
        if (scale <= 0f) {
            throw new FramebufferException("screen scale must be > 0, got " + scale);
        }
        return Framebuffer.createScreen(scale, spec);
    }

    public static Framebuffer captureColor() {
        return screen("Capture Color", FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
    }

    public static Framebuffer captureDepth() {
        return screen("Capture Depth", FramebufferSpec.builder()
                .color(ColorFormat.RGBA8)
                .depthTexture()
                .build());
    }
}
