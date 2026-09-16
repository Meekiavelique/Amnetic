package com.meekdev.amnetic.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public final class WindowGlContextMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/GL");

    @Inject(
            method = "createGlfwWindow",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J",
                    shift = At.Shift.BEFORE
            )
    )
    private static void amnetic$applyGlVersionHints(CallbackInfoReturnable<Long> cir) {
        int major = Integer.getInteger("amnetic.opengl.major", 4);
        int minor = Integer.getInteger("amnetic.opengl.minor", 6);
        boolean debug = Boolean.getBoolean("amnetic.opengl.debug");

        applyHints(major, minor, debug);
        LOGGER.info("Requesting OpenGL {}.{} core context (compute-capable)", major, minor);
    }

    private static void applyHints(int major, int minor, boolean debug) {
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, major);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, minor);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, debug ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
    }
}
