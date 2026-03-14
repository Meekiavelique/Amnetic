package com.meekdev.amnetic.mixin.accessor;

import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(PostEffectProcessor.class)
public interface PostEffectProcessorAccessor {

    @Accessor("passes")
    List<PostEffectPass> amnetic$getPasses();
}
