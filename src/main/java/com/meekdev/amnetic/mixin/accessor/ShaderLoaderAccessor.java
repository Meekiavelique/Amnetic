package com.meekdev.amnetic.mixin.accessor;

import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShaderManager.class)
public interface ShaderLoaderAccessor {

    @Accessor("postChainProjection")
    Projection amnetic$getProjection();

    @Accessor("postChainProjectionMatrixBuffer")
    ProjectionMatrixBuffer amnetic$getProjectionMatrixBuffer();
}
