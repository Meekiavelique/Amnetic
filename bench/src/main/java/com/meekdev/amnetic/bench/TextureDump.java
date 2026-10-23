package com.meekdev.amnetic.bench;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.shadow.internal.SpotShadowAtlas;
import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

final class TextureDump {

    private TextureDump() {}

    static List<String> dump(Path dir, String prefix) {
        List<String> lines = new ArrayList<>();
        var main = Minecraft.getInstance().getMainRenderTarget();
        depth(dir, prefix + "-main_depth", VanillaCompat.depthTextureGlId(main), lines);
        depth(dir, prefix + "-gbuffer_depth", GBufferTargets.INSTANCE.depthGlId(), lines);
        color(dir, prefix + "-gbuffer_normal", GBufferTargets.INSTANCE.normalGlId(), lines);
        Framebuffer ssaoCapture = field("com.meekdev.amnetic.client.ssao.internal.SsaoPass", "capture");
        if (ssaoCapture != null && ssaoCapture.isAllocated()) depth(dir, prefix + "-ssao_depth", ssaoCapture.depthTextureGlId(), lines);
        Framebuffer ao = field("com.meekdev.amnetic.client.ssao.internal.SsaoPass", "ao");
        if (ao != null && ao.isAllocated()) color(dir, prefix + "-ssao_ao", ao.colorTextureGlId(0), lines);
        Framebuffer blurred = field("com.meekdev.amnetic.client.ssao.internal.SsaoPass", "blurred");
        if (blurred != null && blurred.isAllocated()) color(dir, prefix + "-ssao_blur", blurred.colorTextureGlId(0), lines);
        Framebuffer decalDepth = field("com.meekdev.amnetic.client.decal.internal.DecalRenderer", "depthCapture");
        if (decalDepth != null && decalDepth.isAllocated()) depth(dir, prefix + "-decal_depth", decalDepth.depthTextureGlId(), lines);
        depth(dir, prefix + "-spot_atlas", SpotShadowAtlas.glTextureId(), lines);
        return lines;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(String cls, String name) {
        try {
            Class<?> c = Class.forName(cls);
            Object instance = c.getField("INSTANCE").get(null);
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(instance);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static void depth(Path dir, String name, int tex, List<String> lines) {
        read(dir, name, tex, true, lines);
    }

    private static void color(Path dir, String name, int tex, List<String> lines) {
        read(dir, name, tex, false, lines);
    }

    private static void read(Path dir, String name, int tex, boolean depth, List<String> lines) {
        if (tex <= 0) {
            lines.add(name + ": no texture");
            return;
        }
        int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        int format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        int channels = depth ? 1 : 4;
        FloatBuffer buf = MemoryUtil.memAllocFloat(w * h * channels);
        try {
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, depth ? GL11.GL_DEPTH_COMPONENT : GL11.GL_RGBA, GL11.GL_FLOAT, buf);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            int ones = 0, zeros = 0;
            for (int i = 0; i < w * h; i++) {
                float v = buf.get(i * channels);
                if (v >= 0.99999f) ones++;
                if (v <= 0.00001f) zeros++;
                if (v < 0.99999f) { min = Math.min(min, v); max = Math.max(max, v); }
            }
            lines.add(String.format(Locale.ROOT, "%s: tex %d %dx%d fmt 0x%x, x=1: %.1f%%, x=0: %.1f%%, min %.5f max %.5f",
                    name, tex, w, h, format, 100.0 * ones / (w * h), 100.0 * zeros / (w * h),
                    min == Float.MAX_VALUE ? 1f : min, max == -Float.MAX_VALUE ? 1f : max));
            float lo = min == Float.MAX_VALUE ? 0f : min, hi = max <= lo ? lo + 1e-6f : max;
            try (NativeImage img = new NativeImage(w, h, false)) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int i = (y * w + x) * channels;
                        int r, g, b;
                        if (depth) {
                            float v = buf.get(i);
                            int c = v >= 0.99999f ? 255 : (int) (255 * Math.pow((v - lo) / (hi - lo), 0.5));
                            r = g = b = Math.max(0, Math.min(255, c));
                        } else {
                            r = clamp(buf.get(i));
                            g = clamp(buf.get(i + 1));
                            b = clamp(buf.get(i + 2));
                        }
                        img.setPixelRGBA(x, h - 1 - y, 0xFF000000 | (b << 16) | (g << 8) | r);
                    }
                }
                Files.createDirectories(dir);
                img.writeToFile(dir.resolve(name + ".png"));
            }
        } catch (Exception e) {
            lines.add(name + ": dump failed " + e);
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, (int) (Math.abs(v) * 255)));
    }
}
