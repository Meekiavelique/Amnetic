package com.meekdev.amnetic.mixin.accessor;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("mainRenderTarget")
    com.mojang.blaze3d.pipeline.RenderTarget amnetic$getMainRenderTarget();

    @Accessor("globalSettingsUniform")
    GlobalSettingsUniform amnetic$getGlobalSettingsUniform();
}
