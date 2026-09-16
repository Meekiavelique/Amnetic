package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.surface.internal.WorldSurfaceRenderer;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
//? if >=1.21.9 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
//?}
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class SurfaceKeyboardMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    //? if >=1.21.9 {
    private void amnetic$surfaceKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        int key = event.key();
        int modifiers = event.modifiers();
    //?} else {
    /*private void amnetic$surfaceKeyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
    *///?}
        if (action == GLFW.GLFW_RELEASE || !amnetic$eligible()) {
            return;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return;
        }
        if (WorldSurfaceRenderer.INSTANCE.keyPressed(key, modifiers)) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    //? if >=1.21.9 {
    private void amnetic$surfaceCharTyped(long window, CharacterEvent event, CallbackInfo ci) {
        int codepoint = event.codepoint();
    //?} else {
    /*private void amnetic$surfaceCharTyped(long window, int codepoint, int modifiers, CallbackInfo ci) {
    *///?}
        if (!amnetic$eligible()) {
            return;
        }
        if (WorldSurfaceRenderer.INSTANCE.charTyped(codepoint)) {
            ci.cancel();
        }
    }

    private static boolean amnetic$eligible() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.screen == null && minecraft.getOverlay() == null;
    }
}
