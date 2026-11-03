package com.meekdev.amnetic.client.light.internal;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

public final class LightingRegression {
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        long window = 0;
        try {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 1);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
            window = GLFW.glfwCreateWindow(128, 128, "Amnetic lighting regression", 0, 0);
            if (window == 0) throw new IllegalStateException("OpenGL 4.1 context unavailable");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            LightResolveRegression.verify();
            System.out.println("PASS: lighting shader regressions");
        } finally {
            if (window != 0) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
