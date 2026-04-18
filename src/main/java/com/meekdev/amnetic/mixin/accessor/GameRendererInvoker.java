package com.meekdev.amnetic.mixin.accessor;

import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererInvoker {

    @Invoker("getProjectionMatrix")
    Matrix4f amnetic$invokeGetProjectionMatrix(float tickProgress);
}
