package com.meekdev.amnetic.client.render;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProgramWorker {
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/ProgramWorker");
    private static final PriorityBlockingQueue<ProgramLink> QUEUE = new PriorityBlockingQueue<>(64,
            Comparator.comparing((ProgramLink link) -> !link.soon()).thenComparingLong(link -> link.order));

    private static Boolean available;
    private static int threads;

    private ProgramWorker() {}

    static boolean available() {
        if (available == null) {
            available = false;
            int wanted = Integer.getInteger("amnetic.compileThreads", Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 2));
            for (int i = 0; i < wanted; i++) {
                long window = window();
                if (window == 0) break;
                Thread t = new Thread(() -> run(window), "Amnetic shader compile " + (threads + 1));
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                t.start();
                threads++;
            }
            available = threads > 0;
            if (!available) LOG.info("no shared gl context, shaders link on the render thread");
        }
        return available;
    }

    static int threads() {
        return threads;
    }

    static void submit(ProgramLink link) {
        QUEUE.add(link);
    }

    private static long window() {
        long main = VanillaCompat.windowHandle();
        if (main == 0) return 0;
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, GLFW.glfwGetWindowAttrib(main, GLFW.GLFW_CONTEXT_VERSION_MAJOR));
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, GLFW.glfwGetWindowAttrib(main, GLFW.GLFW_CONTEXT_VERSION_MINOR));
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(1, 1, "amnetic shader compile", 0, main);
        GLFW.glfwDefaultWindowHints();
        if (GLFW.glfwGetCurrentContext() != main) GLFW.glfwMakeContextCurrent(main);
        return window;
    }

    private static void run(long window) {
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        while (true) {
            ProgramLink link;
            try {
                link = QUEUE.take();
            } catch (InterruptedException e) {
                return;
            }
            if (link.claim()) link.buildOnWorker();
        }
    }
}
