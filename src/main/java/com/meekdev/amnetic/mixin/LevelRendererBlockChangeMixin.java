package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.shadow.internal.OccluderCache;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererBlockChangeMixin {

    @Inject(method = "blockChanged", at = @At("HEAD"))
    private void amnetic$invalidateShadowOccluders(BlockGetter level, BlockPos pos, BlockState oldState,
                                                   BlockState newState, int flags, CallbackInfo ci) {
        OccluderCache.invalidateAt(pos);
    }
}
