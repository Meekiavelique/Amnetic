package com.meekdev.amnetic.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
//? if >=26.3 {
import org.lwjgl.sdl.SDLMouse;
//?} else {
/*import org.lwjgl.glfw.GLFW;
*///?}
//? if >=26.3 {
//?} else if >=1.21.9 {
/*import net.minecraft.client.Minecraft;
*///?} else {
//?}

public final class KeyInput {
    private KeyInput() {}
    public static final int PRESS = 1;
    public static final int RELEASE = 0;
    //? if >=26.3 {

    public static final int KEY_A = InputConstants.KEY_A;
    public static final int KEY_C = InputConstants.KEY_C;
    public static final int KEY_V = InputConstants.KEY_V;
    public static final int KEY_X = InputConstants.KEY_X;
    public static final int KEY_BACKSPACE = InputConstants.KEY_BACKSPACE;
    public static final int KEY_DELETE = InputConstants.KEY_DELETE;
    public static final int KEY_DOWN = InputConstants.KEY_DOWN;
    public static final int KEY_END = InputConstants.KEY_END;
    public static final int KEY_ENTER = InputConstants.KEY_RETURN;
    public static final int KEY_ESCAPE = InputConstants.KEY_ESCAPE;
    public static final int KEY_HOME = InputConstants.KEY_HOME;
    public static final int KEY_KP_ENTER = InputConstants.KEY_NUMPADENTER;
    public static final int KEY_LEFT = InputConstants.KEY_LEFT;
    public static final int KEY_LEFT_SHIFT = InputConstants.KEY_LSHIFT;
    public static final int KEY_RIGHT = InputConstants.KEY_RIGHT;
    public static final int KEY_RIGHT_SHIFT = InputConstants.KEY_RSHIFT;
    public static final int KEY_TAB = InputConstants.KEY_TAB;
    public static final int KEY_UP = InputConstants.KEY_UP;
    public static final int MOD_SHIFT = InputConstants.MOD_SHIFT;
    public static final int MOD_CONTROL = InputConstants.MOD_CONTROL;
    public static final int MOUSE_BUTTON_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    public static final int MOUSE_BUTTON_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    //?} else {
    /*public static final int KEY_A = GLFW.GLFW_KEY_A;
    public static final int KEY_C = GLFW.GLFW_KEY_C;
    public static final int KEY_V = GLFW.GLFW_KEY_V;
    public static final int KEY_X = GLFW.GLFW_KEY_X;
    public static final int KEY_BACKSPACE = GLFW.GLFW_KEY_BACKSPACE;
    public static final int KEY_DELETE = GLFW.GLFW_KEY_DELETE;
    public static final int KEY_DOWN = GLFW.GLFW_KEY_DOWN;
    public static final int KEY_END = GLFW.GLFW_KEY_END;
    public static final int KEY_ENTER = GLFW.GLFW_KEY_ENTER;
    public static final int KEY_ESCAPE = GLFW.GLFW_KEY_ESCAPE;
    public static final int KEY_HOME = GLFW.GLFW_KEY_HOME;
    public static final int KEY_KP_ENTER = GLFW.GLFW_KEY_KP_ENTER;
    public static final int KEY_LEFT = GLFW.GLFW_KEY_LEFT;
    public static final int KEY_LEFT_SHIFT = GLFW.GLFW_KEY_LEFT_SHIFT;
    public static final int KEY_RIGHT = GLFW.GLFW_KEY_RIGHT;
    public static final int KEY_RIGHT_SHIFT = GLFW.GLFW_KEY_RIGHT_SHIFT;
    public static final int KEY_TAB = GLFW.GLFW_KEY_TAB;
    public static final int KEY_UP = GLFW.GLFW_KEY_UP;
    public static final int MOD_SHIFT = GLFW.GLFW_MOD_SHIFT;
    public static final int MOD_CONTROL = GLFW.GLFW_MOD_CONTROL;
    public static final int MOUSE_BUTTON_LEFT = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    public static final int MOUSE_BUTTON_RIGHT = GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    *///?}

    public static boolean keyDown(int key) {
        //? if >=26.3 {
        return InputConstants.isKeyDown(key);
        //?} else if >=1.21.9 {
        /*return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
        *///?} else {
        /*return InputConstants.isKeyDown(VanillaCompat.windowHandle(), key);
        *///?}
    }

    public static int mouseButton(long window, int button) {
        //? if >=26.3 {
        return (SDLMouse.SDL_GetMouseState(null, null) & (1 << (button - 1))) != 0 ? PRESS : RELEASE;
        //?} else {
        /*return GLFW.glfwGetMouseButton(window, button);
        *///?}
    }

    public static String keyName(int key, int scancode) {
        //? if >=26.3 {
        return InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
        //?} else {
        /*return GLFW.glfwGetKeyName(key, scancode);
        *///?}
    }
}
