package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.compat.IrisCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

/** Workaround for IrisShaders/Iris#3137 on Minecraft 26.1 NeoForge. */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlRenderPass", priority = 1100)
public abstract class IrisNeoForgeSamplerMixin {

    private static final boolean AMNETIC$NEOFORGE = amnetic$isClassPresent("net.neoforged.fml.ModList");

    @Shadow @Final
    protected HashMap<String, Object> samplers;

    @Inject(method = "drawIndexed", at = @At("HEAD"))
    private void amnetic$provideIrisPbrSamplers(int baseVertex, int firstIndex, int indexCount,
                                                int instanceCount, CallbackInfo ci) {
        amnetic$provideIrisPbrSamplers();
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void amnetic$provideIrisPbrSamplers(int firstVertex, int vertexCount, CallbackInfo ci) {
        amnetic$provideIrisPbrSamplers();
    }

    private void amnetic$provideIrisPbrSamplers() {
        if (!AMNETIC$NEOFORGE || !IrisCompat.isShaderPackInUse()) return;
        Object albedo = samplers.get("Sampler0");
        if (albedo == null) return;
        // Iris owns these texture units after Blaze3D validates the pass.
        samplers.putIfAbsent("Sampler1", albedo);
        samplers.putIfAbsent("Sampler2", albedo);
    }

    private static boolean amnetic$isClassPresent(String name) {
        try {
            Class.forName(name, false, IrisNeoForgeSamplerMixin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
