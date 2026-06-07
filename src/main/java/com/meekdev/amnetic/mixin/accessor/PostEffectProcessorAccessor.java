package com.meekdev.amnetic.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;

@Mixin(PostChain.class)
public interface PostEffectProcessorAccessor {

    @Accessor("passes")
    List<PostPass> amnetic$getPasses();
}
