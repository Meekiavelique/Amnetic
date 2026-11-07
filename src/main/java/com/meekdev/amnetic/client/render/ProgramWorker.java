package com.meekdev.amnetic.client.render;

import net.minecraft.util.Mth;
import com.meekdev.amnetic.client.compat.VanillaCompat;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
//? if >=26.3 {
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;
//?} else {
/*import org.lwjgl.glfw.GLFW;
*///?}
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
            int wanted = Integer.getInteger("amnetic.compileThreads", Mth.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 2));
            for (int i = 0; i < wanted; i++) {
                long[] window = window();
                if (window == null) break;
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

    //? if >=26.3 {
    private static long[] window() {
        long main = SDLVideo.SDL_GL_GetCurrentWindow();
        long mainContext = SDLVideo.SDL_GL_GetCurrentContext();
        if (main == 0 || mainContext == 0) return null;
        int major;
        int minor;
        int profile;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var value = stack.mallocInt(1);
            SDLVideo.SDL_GL_GetAttribute(SDLVideo.SDL_GL_CONTEXT_MAJOR_VERSION, value);
            major = value.get(0);
            SDLVideo.SDL_GL_GetAttribute(SDLVideo.SDL_GL_CONTEXT_MINOR_VERSION, value);
            minor = value.get(0);
            SDLVideo.SDL_GL_GetAttribute(SDLVideo.SDL_GL_CONTEXT_PROFILE_MASK, value);
            profile = value.get(0);
        }
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_MAJOR_VERSION, major);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_MINOR_VERSION, minor);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_PROFILE_MASK, profile);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_SHARE_WITH_CURRENT_CONTEXT, 1);
        long window = SDLVideo.SDL_CreateWindow("amnetic shader compile", 1, 1,
                SDLVideo.SDL_WINDOW_OPENGL | SDLVideo.SDL_WINDOW_HIDDEN);
        long context = window == 0 ? 0 : SDLVideo.SDL_GL_CreateContext(window);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_SHARE_WITH_CURRENT_CONTEXT, 0);
        SDLVideo.SDL_GL_MakeCurrent(main, mainContext);
        if (context == 0) {
            if (window != 0) SDLVideo.SDL_DestroyWindow(window);
            return null;
        }
        return new long[]{window, context};
    }

    private static void makeCurrent(long[] window) {
        SDLVideo.SDL_GL_MakeCurrent(window[0], window[1]);
    }
    //?} else {
    /*private static long[] window() {
        long main = VanillaCompat.windowHandle();
        if (main == 0) return null;
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
        return window == 0 ? null : new long[]{window};
    }

    private static void makeCurrent(long[] window) {
        GLFW.glfwMakeContextCurrent(window[0]);
    }
    *///?}

    private static void run(long[] window) {
        makeCurrent(window);
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
