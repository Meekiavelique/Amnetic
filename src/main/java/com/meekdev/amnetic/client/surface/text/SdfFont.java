package com.meekdev.amnetic.client.surface.text;

import com.meekdev.amnetic.client.render.GlState;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.stb.STBTruetype.*;

public final class SdfFont {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Surface");

    static final int BAKE_PX = 64;
    private static final int PADDING = 8;
    private static final byte ONEDGE = (byte) 128;
    private static final float PIXEL_DIST_SCALE = 128f / PADDING;
    private static final int ATLAS = 1024;

    public record Glyph(int ax, int ay, int aw, int ah, float xoff, float yoff, float advance, int source) {}

    private record Source(ByteBuffer data, STBTTFontinfo info, float scale) {}

    private final List<Source> sources;
    private final float ascent;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final int texture;

    private int penX, penY, rowH;
    private boolean atlasFull;

    private SdfFont(List<Source> sources, float ascent, int texture) {
        this.sources = sources;
        this.ascent = ascent;
        this.texture = texture;
    }

    public int texture() { return texture; }
    public int atlasSize() { return ATLAS; }
    public float bakePx() { return BAKE_PX; }
    public float ascentPx() { return ascent; }

    public float sdfUnitsPerGuiPx(float px) {
        return (PIXEL_DIST_SCALE / 255f) * (BAKE_PX / px);
    }

    public float capPx() {
        if (capHeight == 0) {
            Glyph h = glyph('H');
            capHeight = (h != null && h.ah() > 0) ? h.ah() - PADDING * 2 : ascent * 0.72f;
        }
        return capHeight;
    }

    private float capHeight;

    private static final String PREWARM_EXTRA =
            "\u2550\u2551\u2552\u2553\u2554\u2555\u2556\u2557\u2558\u2559\u255A\u255B\u255C\u255D"
            + "\u2500\u2502\u250C\u2510\u2514\u2518\u251C\u2524\u252C\u2534\u253C"
            + "\u2591\u2592\u2593\u2588\u25CF\u25CB\u25A0\u25A1\u2022\u00B7\u2026\u2190\u2192";

    private void prewarm() {
        for (int cp = 32; cp < 127; cp++) {
            glyph(cp);
        }
        PREWARM_EXTRA.codePoints().forEach(this::glyph);
    }

    public boolean baked(int codepoint) {
        return glyphs.containsKey(codepoint);
    }

    public Glyph glyph(int codepoint) {
        Glyph g = glyphs.get(codepoint);
        if (g != null) return g;
        return bake(codepoint);
    }

    public float kern(int cp1, int cp2) {
        Glyph g1 = glyph(cp1), g2 = glyph(cp2);
        if (g1 == null || g2 == null || g1.source() != g2.source()) return 0;
        Source s = sources.get(g1.source());
        return stbtt_GetCodepointKernAdvance(s.info(), cp1, cp2) * s.scale();
    }

    public float width(String s, float px) {
        float f = px / BAKE_PX, w = 0;
        int prev = -1;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            Glyph g = glyph(cp);
            if (g != null) {
                if (prev != -1) w += kern(prev, cp) * f;
                w += g.advance() * f;
            }
            prev = cp;
        }
        return w;
    }

    private int pickSource(int cp) {
        for (int i = 0; i < sources.size(); i++) {
            if (stbtt_FindGlyphIndex(sources.get(i).info(), cp) != 0) return i;
        }
        return 0;
    }

    private Glyph bake(int cp) {
        int srcIdx = pickSource(cp);
        Source src = sources.get(srcIdx);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer adv = stack.mallocInt(1), lsb = stack.mallocInt(1);
            stbtt_GetCodepointHMetrics(src.info(), cp, adv, lsb);
            float advance = adv.get(0) * src.scale();

            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), xo = stack.mallocInt(1), yo = stack.mallocInt(1);
            ByteBuffer sdf = stbtt_GetCodepointSDF(src.info(), src.scale(), cp, PADDING, ONEDGE, PIXEL_DIST_SCALE, w, h, xo, yo);
            if (sdf == null) {
                Glyph g = new Glyph(0, 0, 0, 0, 0, 0, advance, srcIdx);
                glyphs.put(cp, g);
                return g;
            }
            int gw = w.get(0), gh = h.get(0);
            if (penX + gw >= ATLAS) { penX = 0; penY += rowH + 2; rowH = 0; }
            if (penY + gh >= ATLAS) {
                stbtt_FreeSDF(sdf);
                if (!atlasFull) {
                    atlasFull = true;
                    LOG.warn("sdf atlas full, further new glyphs will not render");
                }
                Glyph g = new Glyph(0, 0, 0, 0, 0, 0, advance, srcIdx);
                glyphs.put(cp, g);
                return g;
            }

            GlState.bindTexture(0, texture);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, penX, penY, gw, gh, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, sdf);

            Glyph g = new Glyph(penX, penY, gw, gh, xo.get(0), yo.get(0), advance, srcIdx);
            glyphs.put(cp, g);
            penX += gw + 2;
            rowH = Math.max(rowH, gh);
            stbtt_FreeSDF(sdf);
            return g;
        }
    }

    private static Source loadSource(Identifier fontId) {
        ByteBuffer fontData = null;
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResource(fontId)
                .orElseThrow(() -> new RuntimeException("font not found: " + fontId)).open()) {
            byte[] bytes = in.readAllBytes();
            fontData = MemoryUtil.memAlloc(bytes.length);
            fontData.put(bytes).flip();
            STBTTFontinfo info = STBTTFontinfo.create();
            if (!stbtt_InitFont(info, fontData)) throw new RuntimeException("stbtt_InitFont failed");
            float scale = stbtt_ScaleForPixelHeight(info, BAKE_PX);
            ByteBuffer kept = fontData;
            fontData = null;
            return new Source(kept, info, scale);
        } catch (Exception e) {
            LOG.warn("sdf font bake failed for {}: {}", fontId, e.getMessage());
            return null;
        } finally {
            if (fontData != null) MemoryUtil.memFree(fontData);
        }
    }

    static SdfFont load(Identifier primary, Identifier... fallbacks) {
        Source first = loadSource(primary);
        if (first == null) return null;
        List<Source> sources = new ArrayList<>();
        sources.add(first);
        for (Identifier fb : fallbacks) {
            Source s = loadSource(fb);
            if (s != null) sources.add(s);
        }

        float ascent;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer a = stack.mallocInt(1), d = stack.mallocInt(1), g = stack.mallocInt(1);
            stbtt_GetFontVMetrics(first.info(), a, d, g);
            ascent = a.get(0) * first.scale();
        }

        int tex = GL11.glGenTextures();
        GlState.bindTexture(0, tex);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        ByteBuffer zeros = MemoryUtil.memCalloc(ATLAS * ATLAS);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, ATLAS, ATLAS, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, zeros);
        MemoryUtil.memFree(zeros);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);

        SdfFont font = new SdfFont(sources, ascent, tex);
        font.prewarm();
        return font;
    }
}
