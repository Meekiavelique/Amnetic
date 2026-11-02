package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.emissive.BlockEmissive;
import com.meekdev.amnetic.client.shadow.internal.OccluderCache;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.2 {
@Mixin(net.minecraft.client.renderer.extract.LevelExtractor.class)
//?} else {
/*@Mixin(LevelRenderer.class)
*///?}
public abstract class LevelRendererBlockChangeMixin {

    @Inject(method = "blockChanged", at = @At("HEAD"))
    //? if >=26.2 {
    private void amnetic$invalidateShadowOccluders(BlockPos pos, int flags, CallbackInfo ci) {
        OccluderCache.invalidateAt(pos);
        BlockEmissive.invalidate();
    }
    //?} else {
    /*private void amnetic$invalidateShadowOccluders(BlockGetter level, BlockPos pos, BlockState oldState,
                                                   BlockState newState, int flags, CallbackInfo ci) {
        OccluderCache.invalidateAt(pos);
        if (oldState.getLightEmission() != newState.getLightEmission()) {
            BlockEmissive.invalidate();
        }
    }
    *///?}
}
