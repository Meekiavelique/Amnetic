package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.surface.internal.WorldSurfaceRenderer;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class SurfaceKeyboardMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void amnetic$surfaceKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action == GLFW.GLFW_RELEASE || !amnetic$eligible()) {
            return;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            return;
        }
        if (WorldSurfaceRenderer.INSTANCE.keyPressed(event.key(), event.modifiers())) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void amnetic$surfaceCharTyped(long window, CharacterEvent event, CallbackInfo ci) {
        if (!amnetic$eligible()) {
            return;
        }
        if (WorldSurfaceRenderer.INSTANCE.charTyped(event.codepoint())) {
            ci.cancel();
        }
    }

    private static boolean amnetic$eligible() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.screen == null && minecraft.getOverlay() == null;
    }
}
