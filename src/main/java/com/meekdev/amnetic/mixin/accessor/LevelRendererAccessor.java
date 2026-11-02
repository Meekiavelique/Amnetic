package com.meekdev.amnetic.mixin.accessor;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
//? if >=26.2 {
/*import net.minecraft.client.renderer.state.level.CameraRenderState;
*///?}
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {

    @Accessor("visibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> amnetic$getVisibleSections();

    @Mutable
    @Accessor("visibleSections")
    void amnetic$setVisibleSections(ObjectArrayList<SectionRenderDispatcher.RenderSection> sections);

    //? if >=1.21.2 {
    @Accessor("nearbyVisibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> amnetic$getNearbyVisibleSections();

    @Mutable
    @Accessor("nearbyVisibleSections")
    void amnetic$setNearbyVisibleSections(ObjectArrayList<SectionRenderDispatcher.RenderSection> sections);
    //?}

    //? if >=26.2 {
    @Accessor("targets")
    net.minecraft.client.renderer.LevelTargetBundle amnetic$getTargets();
    //?} else {
    /*@Invoker("compileSections")
    void amnetic$compileSections(Camera camera);
    *///?}
}
