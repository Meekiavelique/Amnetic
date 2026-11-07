package com.meekdev.amnetic.client.render;

//? if >=26.2 && <26.3 {
/*import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.function.Supplier;
import com.mojang.blaze3d.systems.RenderPass;

public final class NativePassDescriptors {
    public static Builder builder(Supplier<String> label) { return new Builder(label); }
    public static final class Builder {
        private final RenderPassDescriptor descriptor;
        private Builder(Supplier<String> label) { descriptor = RenderPassDescriptor.create(label); }
        public Builder withColorAttachment(GpuTextureView view) {
            descriptor.withColorAttachment(view);
            descriptor.withRenderArea(new RenderPass.RenderArea(0,0,view.getWidth(0),view.getHeight(0)));
            return this;
        }
        public Builder withDepthAttachment(GpuTextureView view) { descriptor.withDepthAttachment(view); return this; }
        public RenderPassDescriptor build() { return descriptor; }
    }
}
*///? }
