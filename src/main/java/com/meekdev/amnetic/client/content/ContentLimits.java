package com.meekdev.amnetic.client.content;

public final class ContentLimits {
    public static final int IMAGE_SIDE = 16384;
    public static final int IMAGE_PIXELS = 8192 * 4096;
    public static final long MODEL_DATA_BYTES = 256L * 1024 * 1024;
    public static final int MODEL_NODES = 65536;
    public static final int NODE_DEPTH = 512;
    public static final int JSON_DEPTH = 128;
    public static final int INFLATED_BYTES = 64 * 1024 * 1024;
    public static final int FONT_BYTES = 32 * 1024 * 1024;
    public static final int FONT_TABLES = 256;
    public static final int FONT_GLYPH_POINTS = 65536;
    public static final int FONT_COMPOSITE_DEPTH = 16;

    private ContentLimits() {}
}
