package com.meekdev.amnetic.mixin.accessor;

import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ShaderManager.class)
public interface ShaderLoaderAccessor {

    @Accessor("postChainProjection")
    Projection amnetic$getProjection();

    @Accessor("postChainProjectionMatrixBuffer")
    ProjectionMatrixBuffer amnetic$getProjectionMatrixBuffer();

    @Invoker("prepare")
    ShaderManager.Configs amnetic$prepare(ResourceManager resourceManager, ProfilerFiller profiler);

    @Invoker("apply")
    void amnetic$apply(ShaderManager.Configs configs, ResourceManager resourceManager, ProfilerFiller profiler);
}
