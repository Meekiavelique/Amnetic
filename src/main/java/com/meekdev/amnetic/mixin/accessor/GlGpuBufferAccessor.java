package com.meekdev.amnetic.mixin.accessor;

import net.minecraft.client.gl.GlGpuBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlGpuBuffer.class)
public interface GlGpuBufferAccessor {

    @Accessor("id")
    int amnetic$getId();
}
