package com.meekdev.amnetic.client.instanced;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL33;

import java.util.ArrayList;
import java.util.List;

public final class InstanceLayout {

    public static final InstanceLayout TRANSFORM_COLOR = builder().mat4(1).vec4(5).build();
    public static final InstanceLayout TRANSFORM       = builder().mat4(1).build();

    private final List<AttributeSpec> attributes;
    private final int stride;

    private InstanceLayout(List<AttributeSpec> attributes, int stride) {
        this.attributes = attributes;
        this.stride = stride;
    }

    public int stride() { return stride; }

    public void setupVaoAttributes() {
        for (AttributeSpec spec : attributes) {
            GlStateManager._vertexAttribPointer(spec.location, spec.components, GL11.GL_FLOAT, false, stride, spec.byteOffset);
            GlStateManager._enableVertexAttribArray(spec.location);
            GL33.glVertexAttribDivisor(spec.location, 1);
        }
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<AttributeSpec> attributes = new ArrayList<>();
        private int offset = 0;

        public Builder mat4(int startLocation) {
            for (int i = 0; i < 4; i++) {
                attributes.add(new AttributeSpec(startLocation + i, 4, offset + i * 16));
            }
            offset += 64;
            return this;
        }

        public Builder vec4(int location) {
            attributes.add(new AttributeSpec(location, 4, offset));
            offset += 16;
            return this;
        }

        public Builder vec3(int location) {
            attributes.add(new AttributeSpec(location, 3, offset));
            offset += 12;
            return this;
        }

        public Builder vec2(int location) {
            attributes.add(new AttributeSpec(location, 2, offset));
            offset += 8;
            return this;
        }

        public Builder float1(int location) {
            attributes.add(new AttributeSpec(location, 1, offset));
            offset += 4;
            return this;
        }

        public InstanceLayout build() {
            return new InstanceLayout(List.copyOf(attributes), offset);
        }
    }

    private record AttributeSpec(int location, int components, int byteOffset) {}
}
