package com.meekdev.amnetic.client.instanced;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class RenderState {

    public static final RenderState DEFAULT     = new RenderState(true,  true,  BlendMode.NONE,     true);

    public static final RenderState TRANSLUCENT = new RenderState(true,  false, BlendMode.ALPHA,    true);

    public static final RenderState ADDITIVE    = new RenderState(true,  false, BlendMode.ADDITIVE, false);

    private final boolean depthTest;
    private final boolean depthWrite;
    private final BlendMode blendMode;
    private final boolean backfaceCulling;

    private RenderState(boolean depthTest, boolean depthWrite, BlendMode blendMode, boolean backfaceCulling) {
        this.depthTest = depthTest;
        this.depthWrite = depthWrite;
        this.blendMode = blendMode;
        this.backfaceCulling = backfaceCulling;
    }

    public enum BlendMode {
        NONE, ALPHA, ADDITIVE, MULTIPLY
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean depthTest = true;
        private boolean depthWrite = true;
        private BlendMode blendMode = BlendMode.NONE;
        private boolean backfaceCulling = true;

        public Builder depthTest(boolean v)      { depthTest = v; return this; }
        public Builder depthWrite(boolean v)     { depthWrite = v; return this; }
        public Builder blend(BlendMode mode)     { blendMode = mode; return this; }
        public Builder backfaceCulling(boolean v){ backfaceCulling = v; return this; }
        public RenderState build() { return new RenderState(depthTest, depthWrite, blendMode, backfaceCulling); }
    }

    public void apply() {
        if (depthTest) GlStateManager._enableDepthTest(); else GlStateManager._disableDepthTest();
        GlStateManager._depthMask(depthWrite);
        if (backfaceCulling) GlStateManager._enableCull(); else GlStateManager._disableCull();
        switch (blendMode) {
            case NONE -> GlStateManager._disableBlend();
            case ALPHA -> {
                GlStateManager._enableBlend();
                GlStateManager._blendFuncSeparate(
                        GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_ONE,       GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
            case ADDITIVE -> {
                GlStateManager._enableBlend();
                GlStateManager._blendFuncSeparate(
                        GL11.GL_SRC_ALPHA, GL11.GL_ONE,
                        GL11.GL_ONE,       GL11.GL_ONE);
            }
            case MULTIPLY -> {
                GlStateManager._enableBlend();
                GlStateManager._blendFuncSeparate(
                        GL11.GL_DST_COLOR, GL11.GL_ZERO,
                        GL11.GL_ONE,       GL11.GL_ZERO);
            }
        }
    }

    public void restore() {
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        GlStateManager._enableCull();
    }
}
