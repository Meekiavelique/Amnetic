package com.meekdev.amnetic.mixin;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GlStateManager.class)
public abstract class NativeTextureUnitsMixin {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target = "Ljava/util/stream/IntStream;range(II)Ljava/util/stream/IntStream;"), index = 1)
    private static int amnetic$textureUnits(int count) {
        return Math.max(count, 16);
    }
}
