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

@Mixin(Window.class)
public final class WindowGlContextMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/GL");

    @WrapOperation(
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
                                                          long monitor, long share,
                                                          Operation<Long> create) {
        boolean mac = Platform.get() == Platform.MACOSX;
        int major = Integer.getInteger("amnetic.opengl.major", 4);
        int minor = Integer.getInteger("amnetic.opengl.minor", mac ? 1 : 6);
        boolean debug = Boolean.getBoolean("amnetic.opengl.debug");

        applyHints(major, minor, debug);
        long handle = create.call(width, height, title, monitor, share);
        if (handle != 0L) {
            LOGGER.info("Created OpenGL {}.{} core context", major, minor);
            return handle;
        }

        LOGGER.warn("OpenGL {}.{} context creation failed; falling back to 3.3", major, minor);
        applyHints(3, 3, false);
        return create.call(width, height, title, monitor, share);
    }

    private static void applyHints(int major, int minor, boolean debug) {
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, major);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, minor);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, debug ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
    }
}
