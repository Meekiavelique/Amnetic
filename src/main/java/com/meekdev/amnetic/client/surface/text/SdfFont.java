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

// runtime single-channel SDF glyph atlas from a ttf, glyphs bake on demand so any
// codepoint works without a prebaked charset, one bake size serves every on-screen size,
// a fallback chain of extra fonts covers codepoints the primary is missing, all sources
// bake into the same shared atlas
public final class SdfFont {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Surface");

    // 64px em, big enough that sharp corners survive the distance field
    static final int BAKE_PX = 64;
    private static final int PADDING = 8; // sdf spread each side of the edge, texels
    private static final byte ONEDGE = (byte) 128; // atlas value at the edge, shader threshold 0.5
    private static final float PIXEL_DIST_SCALE = 128f / PADDING;
    private static final int ATLAS = 1024;

    // source tracks which font in the chain resolved the codepoint, kerning only applies
    // when both codepoints came from the same source
    public record Glyph(int ax, int ay, int aw, int ah, float xoff, float yoff, float advance, int source) {}

    // one font in the chain, data kept alive because stb reads from it on every bake
    private record Source(ByteBuffer data, STBTTFontinfo info, float scale) {}

    private final List<Source> sources;
    private final float ascent; // from the primary
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

    // sdf units per gui pixel at the given draw size, converts pixel widths (outline,
    // glow radius) into the shader's edge-offset/softness space; reach is clamped by the
    // baked spread, roughly px/8 gui pixels
    public float sdfUnitsPerGuiPx(float px) {
        return (PIXEL_DIST_SCALE / 255f) * (BAKE_PX / px);
    }

    // real cap height measured off the H glyph, ascent overshoots it and miscenters labels
    public float capPx() {
        if (capHeight == 0) {
            Glyph h = glyph('H');
            capHeight = (h != null && h.ah() > 0) ? h.ah() - PADDING * 2 : ascent * 0.72f;
        }
        return capHeight;
    }

    private float capHeight;

    // bakes the codepoint on first request, render thread only
    public Glyph glyph(int codepoint) {
        Glyph g = glyphs.get(codepoint);
        if (g != null) return g;
        return bake(codepoint);
    }

    // kerning only makes sense inside one font, cross-source pairs get none
    public float kern(int cp1, int cp2) {
        Glyph g1 = glyph(cp1), g2 = glyph(cp2);
        if (g1 == null || g2 == null || g1.source() != g2.source()) return 0;
        Source s = sources.get(g1.source());
        return stbtt_GetCodepointKernAdvance(s.info(), cp1, cp2) * s.scale();
    }

    // advance width at target pixel height, kerning included
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

    // first source in the chain that actually has a shape for the codepoint, primary if none
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
            if (sdf == null) { // no shape (space etc), advance only
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

            GlState.bindTexture(0, texture); // raw + cache sync, keeps vanilla's next bind honest
            // reset every unpack param, vanilla leaves skip/row offsets behind and a partial
            // reset uploads glyphs from the wrong byte offsets (garbled atlas rows)
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, penX, penY, gw, gh, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, sdf);

            Glyph g = new Glyph(penX, penY, gw, gh, xo.get(0), yo.get(0), advance, srcIdx);
            glyphs.put(cp, g);
            penX += gw + 2; // 2px gap so bilinear sampling never bleeds a neighbour in
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
            fontData = null; // ownership moves to the source, freed never (lives for the session)
            return new Source(kept, info, scale);
        } catch (Exception e) {
            LOG.warn("sdf font bake failed for {}: {}", fontId, e.getMessage());
            return null;
        } finally {
            if (fontData != null) MemoryUtil.memFree(fontData);
        }
    }

    // null (logged) if the primary can't be read, failed fallbacks are skipped, render thread
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
        // zero-filled, null data leaves undefined vram that bleeds around glyph edges
        ByteBuffer zeros = MemoryUtil.memCalloc(ATLAS * ATLAS);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, ATLAS, ATLAS, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, zeros);
        MemoryUtil.memFree(zeros);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);

        return new SdfFont(sources, ascent, tex);
    }
}
