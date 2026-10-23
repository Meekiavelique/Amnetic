package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.emissive.BlockEmissive;
import com.meekdev.amnetic.client.shadow.internal.OccluderCache;
import net.minecraft.core.BlockPos;
//? if >=26.2 {
/*import net.minecraft.client.multiplayer.ClientLevel;
*///?} else {
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.BlockGetter;
//?}
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.2 dropped the block states from the renderer hook, the level still has them
//? if >=26.2 {
/*@Mixin(ClientLevel.class)
*///?} else {
@Mixin(LevelRenderer.class)
//?}
public abstract class LevelRendererBlockChangeMixin {

    //? if >=26.2 {
    /*@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void amnetic$invalidateShadowOccluders(BlockPos pos, BlockState oldState,
                                                   BlockState newState, int flags, CallbackInfo ci) {
    *///?} else {
    @Inject(method = "blockChanged", at = @At("HEAD"))
    private void amnetic$invalidateShadowOccluders(BlockGetter level, BlockPos pos, BlockState oldState,
                                                   BlockState newState, int flags, CallbackInfo ci) {
    //?}
        OccluderCache.invalidateAt(pos);
        if (oldState.getLightEmission() != newState.getLightEmission()) {
            BlockEmissive.invalidate();
        }
    }
}
