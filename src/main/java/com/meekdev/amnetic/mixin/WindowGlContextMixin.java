package com.meekdev.amnetic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.lwjgl.glfw.GLFWErrorCallback;

@Mixin(Window.class)
public final class WindowGlContextMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/GL");

    @WrapOperation(
            require = 0,
            //? if >=26.1 {
            method = "createGlfwWindow",
            //?} else {
            /*method = "<init>",
            *///?}
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"
            )
    )
    private static long amnetic$createWindowWithGlVersion(int width, int height, CharSequence title,
                                                          long monitor, long share, Operation<Long> original) {
        boolean mac = Platform.get() == Platform.MACOSX;
        int major = Integer.getInteger("amnetic.opengl.major", 4);
        int minor = Integer.getInteger("amnetic.opengl.minor", mac ? 1 : 6);
        boolean debug = Boolean.getBoolean("amnetic.opengl.debug");

        GLFWErrorCallback probe = GLFWErrorCallback.create((code, description) ->
                LOGGER.warn("Context probe {}: {}", code, GLFWErrorCallback.getDescription(description)));
        GLFWErrorCallback previous = GLFW.glfwSetErrorCallback(probe);
        try {
            int[][] candidates = mac ? new int[][]{{major, minor}, {4, 1}, {3, 3}} : new int[][]{{major, minor}, {4, 3}, {3, 3}};
            for (int[] version : candidates) {
                applyHints(version[0], version[1], debug);
                long handle = original.call(width, height, title, monitor, share);
                if (handle != 0L) return handle;
            }
            throw new IllegalStateException("Unable to create an OpenGL 3.3 or newer context");
        } finally {
            GLFW.glfwSetErrorCallback(previous);
            probe.free();
        }
    }

    private static void applyHints(int major, int minor, boolean debug) {
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, major);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, minor);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, debug ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        if (debug) GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_NO_ERROR, GLFW.GLFW_FALSE);
    }
}
