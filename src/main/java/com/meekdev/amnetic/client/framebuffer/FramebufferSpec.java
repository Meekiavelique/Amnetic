package com.meekdev.amnetic.client.framebuffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FramebufferSpec {

    public static final int MAX_COLOR_ATTACHMENTS = 8;

    private final List<ColorFormat> colorFormats;
    private final DepthMode depthMode;

    private FramebufferSpec(List<ColorFormat> colorFormats, DepthMode depthMode) {
        this.colorFormats = Collections.unmodifiableList(colorFormats);
        this.depthMode = depthMode;
    }

    public List<ColorFormat> colorFormats() {
        return colorFormats;
    }

    public int colorCount() {
        return colorFormats.size();
    }

    public DepthMode depthMode() {
        return depthMode;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<ColorFormat> colors = new ArrayList<>();
        private DepthMode depthMode = DepthMode.NONE;

        private Builder() {}

        public Builder color(ColorFormat format) {
            colors.add(format);
            return this;
        }

        public Builder depthTexture() {
            this.depthMode = DepthMode.TEXTURE;
            return this;
        }

        public Builder depthRenderbuffer() {
            this.depthMode = DepthMode.RENDERBUFFER;
            return this;
        }

        public FramebufferSpec build() {
            if (colors.isEmpty()) {
                throw new FramebufferException("Framebuffer spec needs at least one color attachment");
            }
            if (colors.size() > MAX_COLOR_ATTACHMENTS) {
                throw new FramebufferException(
                        "Framebuffer spec has " + colors.size() + " color attachments; max is "
                                + MAX_COLOR_ATTACHMENTS);
            }
            return new FramebufferSpec(new ArrayList<>(colors), depthMode);
        }
    }
}
