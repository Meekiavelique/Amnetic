package com.meekdev.amnetic.mixin;

import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Window.class)
public final class WindowGlContextMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/GL");

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"
            )
    )
    private long amnetic$createWindowWithOptionalGlVersion(int width, int height, CharSequence title, long monitor, long share) {
        int major = Integer.getInteger("amnetic.opengl.major", -1);
        int minor = Integer.getInteger("amnetic.opengl.minor", -1);
        boolean debug = Boolean.getBoolean("amnetic.opengl.debug");

        boolean hasOverride = major > 0 && minor >= 0;
        if (hasOverride) {
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, major);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, minor);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, debug ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        }

        long handle = GLFW.glfwCreateWindow(width, height, title, monitor, share);
        if (handle != 0L) {
            if (hasOverride) {
                LOGGER.info("Created OpenGL context {}.{} debug={}", major, minor, debug);
            }
            return handle;
        }

        if (hasOverride) {
            LOGGER.warn("Failed to create OpenGL {}.{} context, retrying with 3.3", major, minor);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, GLFW.GLFW_FALSE);
            return GLFW.glfwCreateWindow(width, height, title, monitor, share);
        }

        return 0L;
    }
}

