package com.meekdev.amnetic.bench;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLDebugMessageCallback;

final class GlDebug {

    private static final int MAX_TRACES = 12;
    private static final List<String> TRACES = new ArrayList<>();
    private static GLDebugMessageCallback callback;
    private static String scene = "";

    private GlDebug() {}

    static void install() {
        if (callback != null || !GL.getCapabilities().OpenGL43) return;
        callback = GLDebugMessageCallback.create((source, type, id, severity, length, message, user) -> {
            if (type != GL43.GL_DEBUG_TYPE_ERROR) return;
            synchronized (TRACES) {
                if (TRACES.size() >= MAX_TRACES) return;
                StringBuilder sb = new StringBuilder();
                sb.append('[').append(scene).append("] ").append(GLDebugMessageCallback.getMessage(length, message)).append('\n');
                int shown = 0;
                for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                    String c = e.getClassName();
                    if (c.startsWith("org.lwjgl") || c.startsWith("java.") || c.startsWith("com.meekdev.amnetic.bench.GlDebug")) continue;
                    sb.append("        at ").append(e).append('\n');
                    if (++shown >= 14) break;
                }
                TRACES.add(sb.toString());
            }
        });
        GL11.glEnable(GL43.GL_DEBUG_OUTPUT);
        GL11.glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
        GL43.glDebugMessageCallback(callback, 0L);
    }

    static void scene(String name) {
        scene = name;
    }

    static List<String> traces() {
        synchronized (TRACES) {
            return List.copyOf(TRACES);
        }
    }
}
