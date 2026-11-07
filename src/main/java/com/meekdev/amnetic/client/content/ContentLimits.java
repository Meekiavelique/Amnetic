package com.meekdev.amnetic.client.content;

// the most a model, texture, image or font from a game may claim, checked before anything is allocated
// keep site/docs in moud in step when one changes
public final class ContentLimits {

    // widest or tallest image or texture, the size most drivers top out at
    public static final int IMAGE_SIDE = 16384;
    // 8192 by 4096, an equirect sky, about 128 MB once decoded to rgba
    public static final int IMAGE_PIXELS = 8192 * 4096;
    // all the accessor data of one gltf, what its arrays take once read
    public static final long MODEL_DATA_BYTES = 256L * 1024 * 1024;
    public static final int MODEL_NODES = 65536;
    // nodes from a root down to the deepest one, walkers recurse this deep
    public static final int NODE_DEPTH = 512;
    // brackets open at once in a json file a library parses for us
    public static final int JSON_DEPTH = 128;
    // what a compressed .bbmodel may grow to
    public static final int INFLATED_BYTES = 64 * 1024 * 1024;
    public static final int FONT_BYTES = 32 * 1024 * 1024;
    public static final int FONT_TABLES = 256;
    // outline points of one glyph once every part of a composite glyph is counted
    public static final int FONT_GLYPH_POINTS = 65536;
    // composite glyphs inside composite glyphs, stb_truetype recurses this deep
    public static final int FONT_COMPOSITE_DEPTH = 16;

    private ContentLimits() {}
}
