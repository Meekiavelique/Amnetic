package com.meekdev.amnetic.client.framebuffer;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.framebuffer.internal.FramebufferRegistry;
import com.meekdev.amnetic.client.framebuffer.internal.GlFramebuffer;
import com.meekdev.amnetic.client.framebuffer.internal.RegisteredColorTexture;
//? if >=26.2 {
import com.meekdev.amnetic.client.framebuffer.internal.NativeFramebuffer;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
//? if >=26.2 {
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTextureView;
//?}

public final class Framebuffer {
    private final GlFramebuffer gl;
    private final boolean screenTracking;
    private final float scale;
    private final int fixedWidth;
    private final int fixedHeight;
    private RegisteredColorTexture registered;
    private boolean disposed;
    //? if >=26.2 {
    private final NativeFramebuffer nativeFramebuffer;
    //?}

    private Framebuffer(GlFramebuffer gl, boolean screenTracking, float scale,
                        int fixedWidth, int fixedHeight) {
        this.gl = gl;
        this.screenTracking = screenTracking;
        this.scale = scale;
        this.fixedWidth = fixedWidth;
        this.fixedHeight = fixedHeight;
        //? if >=26.2 {
        nativeFramebuffer = new NativeFramebuffer(gl.spec(), gl.name());
        FramebufferRegistry.INSTANCE.register(nativeFramebuffer);
        //?}
        FramebufferRegistry.INSTANCE.register(gl);
    }

    public static Framebuffer createFixed(int width, int height, FramebufferSpec spec) {
        return createFixed(null, width, height, spec);
    }

    public static Framebuffer createFixed(String name, int width, int height, FramebufferSpec spec) {
        return new Framebuffer(new GlFramebuffer(spec, name), false, 1f, width, height);
    }

    public static Framebuffer createScreen(float scale, FramebufferSpec spec) {
        return createScreen(null, scale, spec);
    }

    public static Framebuffer createScreen(String name, float scale, FramebufferSpec spec) {
        return new Framebuffer(new GlFramebuffer(spec, name), true, scale, 0, 0);
    }

    public int width() {
        //? if >=26.2 {
        return nativeFramebuffer.width();
        //?} else {
        /*return gl.width();
        *///?}
    }

    public int height() {
        //? if >=26.2 {
        return nativeFramebuffer.height();
        //?} else {
        /*return gl.height();
        *///?}
    }

    public boolean isAllocated() {
        //? if >=26.2 {
        return nativeFramebuffer.isAllocated();
        //?} else {
        /*return gl.isAllocated();
        *///?}
    }

    public int colorTextureGlId(int index) {
        //? if >=26.2 {
        if (!nativeFramebuffer.isAllocated()) return 0;
        return VanillaCompat.glId(nativeFramebuffer.color(index));
        //?} else {
        /*return gl.colorTextureGlId(index);
        *///?}
    }

    public int depthTextureGlId() {
        //? if >=26.2 {
        return nativeFramebuffer.depth() == null ? 0
                : VanillaCompat.glId(nativeFramebuffer.depth());
        //?} else {
        /*return gl.depthTextureGlId();
        *///?}
    }

    public void begin() {
        ensureAllocated();
        gl.begin();
    }

    public void clear(float r, float g, float b, float a) {
        //? if >=26.2 {
        clearNative(r, g, b, a);
        //?} else {
        /*gl.clear(r, g, b, a);
        *///?}
    }

    public void end() {
        gl.end();
    }

    public void blitColorFromMain() {
        //? if >=26.2 {
        ensureNativeAllocated();
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main.width != width() || main.height != height() || main.getColorTexture().getFormat() != nativeFramebuffer.color(0).getFormat()) {
            NativeFramebuffer.blitColor(main.getColorTextureView(), nativeFramebuffer.colorView(0));
            return;
        }
        var encoder = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(main.getColorTexture(), nativeFramebuffer.color(0), 0, 0, 0, 0, 0, width(), height());
        encoder.submit();
        //?} else {
        /*ensureAllocated();
        gl.blitColorFromMain();
        *///?}
    }

    public void blitDepthFromMain() {
        //? if >=26.2 {
        ensureNativeAllocated();
        if (nativeFramebuffer.depth() == null) throw new FramebufferException("Framebuffer has no depth attachment");
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main.width != width() || main.height != height())
            throw new FramebufferException("Depth copy requires matching dimensions");
        var encoder = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(main.getDepthTexture(), nativeFramebuffer.depth(), 0, 0, 0, 0, 0, width(), height());
        encoder.submit();
        //?} else {
        /*ensureAllocated();
        gl.blitDepthFromMain();
        *///?}
    }

    public void blitDepthFrom(int srcDepthGlId, int srcW, int srcH) {
        ensureAllocated();
        gl.blitDepthFrom(srcDepthGlId, srcW, srcH);
    }

    public void blitColorToMain() {
        //? if >=26.2 {
        ensureNativeAllocated();
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main.width != width() || main.height != height() || main.getColorTexture().getFormat() != nativeFramebuffer.color(0).getFormat()) {
            NativeFramebuffer.blitColor(nativeFramebuffer.colorView(0), main.getColorTextureView());
            return;
        }
        var encoder = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(nativeFramebuffer.color(0), main.getColorTexture(), 0, 0, 0, 0, 0, width(), height());
        encoder.submit();
        //?} else {
        /*ensureAllocated();
        gl.blitColorToMain();
        *///?}
    }

    public void bindSampler(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, gl.colorTextureGlId(0));
    }

    public void registerColorTexture(Identifier id) {
        //? if >=26.2 {
        ensureNativeAllocated();
        if (registered == null) registered = new RegisteredColorTexture(id);
        registered.update(nativeFramebuffer.colorView(0));
        //?} else {
        /*ensureAllocated();
        if (registered == null) {
            registered = new RegisteredColorTexture(id);
        }
        registered.update(gl.colorTextureGlId(0), gl.width(), gl.height());
        *///?}
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (registered != null) { registered.close(); registered = null; }
        //? if >=26.2 {
        FramebufferRegistry.INSTANCE.deregister(nativeFramebuffer);
        nativeFramebuffer.close();
        //?}
        FramebufferRegistry.INSTANCE.deregister(gl);
        gl.dispose();
    }

    //? if >=26.2 {
    public GpuTextureView colorTextureView(int index) {
        ensureNativeAllocated();
        return nativeFramebuffer.colorView(index);
    }

    public GpuTextureView depthTextureView() {
        ensureNativeAllocated();
        return nativeFramebuffer.depthView();
    }

    public RenderPassDescriptor renderPassDescriptor() {
        ensureNativeAllocated();
        return nativeFramebuffer.descriptor();
    }

    public void clearNative(float r, float g, float b, float a) {
        ensureNativeAllocated();
        nativeFramebuffer.clear(r, g, b, a);
    }

    private void ensureNativeAllocated() {
        if (disposed) throw new FramebufferException("Framebuffer used after dispose()");
        if (screenTracking) {
            var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            nativeFramebuffer.allocate(Math.max(1, Math.round(main.width * scale)),
                    Math.max(1, Math.round(main.height * scale)));
        } else nativeFramebuffer.allocate(fixedWidth, fixedHeight);
        if (registered != null) registered.update(nativeFramebuffer.colorView(0));
    }
    //?}

    private void ensureAllocated() {
        if (disposed) {
            throw new FramebufferException("Framebuffer used after dispose()");
        }
        if (screenTracking) {
            var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            int w = Math.max(1, Math.round(main.width * scale));
            int h = Math.max(1, Math.round(main.height * scale));
            gl.allocate(w, h);
        } else {
            gl.allocate(fixedWidth, fixedHeight);
        }
    }
}
