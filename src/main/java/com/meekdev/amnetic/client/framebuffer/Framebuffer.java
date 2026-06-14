package com.meekdev.amnetic.client.framebuffer;

import com.meekdev.amnetic.client.framebuffer.internal.FramebufferRegistry;
import com.meekdev.amnetic.client.framebuffer.internal.GlFramebuffer;
import com.meekdev.amnetic.client.framebuffer.internal.RegisteredColorTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public final class Framebuffer {

    private final GlFramebuffer gl;
    private final boolean screenTracking;
    private final float scale;       // only meaningful when screenTracking
    private final int fixedWidth;    // only meaningful when !screenTracking
    private final int fixedHeight;
    private RegisteredColorTexture registered;
    private boolean disposed;

    private Framebuffer(GlFramebuffer gl, boolean screenTracking, float scale,
                        int fixedWidth, int fixedHeight) {
        this.gl = gl;
        this.screenTracking = screenTracking;
        this.scale = scale;
        this.fixedWidth = fixedWidth;
        this.fixedHeight = fixedHeight;
        FramebufferRegistry.INSTANCE.register(gl);
    }

    public static Framebuffer createFixed(int width, int height, FramebufferSpec spec) {
        return new Framebuffer(new GlFramebuffer(spec), false, 1f, width, height);
    }

    public static Framebuffer createScreen(float scale, FramebufferSpec spec) {
        return new Framebuffer(new GlFramebuffer(spec), true, scale, 0, 0);
    }

    public int width() {
        return gl.width();
    }

    public int height() {
        return gl.height();
    }

    public int colorTextureGlId(int index) {
        return gl.colorTextureGlId(index);
    }

    public int depthTextureGlId() {
        return gl.depthTextureGlId();
    }

    public void begin() {
        ensureAllocated();
        gl.begin();
    }

    public void clear(float r, float g, float b, float a) {
        gl.clear(r, g, b, a);
    }

    public void end() {
        gl.end();
    }

    public void blitColorFromMain() {
        ensureAllocated();
        gl.blitColorFromMain();
    }

    public void blitDepthFromMain() {
        ensureAllocated();
        gl.blitDepthFromMain();
    }

    public void blitDepthFrom(int srcDepthGlId, int srcW, int srcH) {
        ensureAllocated();
        gl.blitDepthFrom(srcDepthGlId, srcW, srcH);
    }

    public void blitColorToMain() {
        ensureAllocated();
        gl.blitColorToMain();
    }

    public void bindSampler(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, gl.colorTextureGlId(0));
    }

    public void registerColorTexture(Identifier id) {
        ensureAllocated();
        if (registered == null) {
            registered = new RegisteredColorTexture(id);
        }
        registered.update(gl.colorTextureGlId(0), gl.width(), gl.height());
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        FramebufferRegistry.INSTANCE.deregister(gl);
        gl.dispose();
    }

    private void ensureAllocated() {
        if (disposed) {
            throw new FramebufferException("Framebuffer used after dispose()");
        }
        if (screenTracking) {
            var main = Minecraft.getInstance().getMainRenderTarget();
            int w = Math.max(1, Math.round(main.width * scale));
            int h = Math.max(1, Math.round(main.height * scale));
            gl.allocate(w, h);
        } else {
            gl.allocate(fixedWidth, fixedHeight);
        }
    }
}
