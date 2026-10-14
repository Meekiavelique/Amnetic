package com.meekdev.amnetic.client.compat;

import com.meekdev.amnetic.platform.Platform;
import java.lang.reflect.Method;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.slf4j.LoggerFactory;

/** Optional Iris access; neither Iris nor a shader pack is required. */
public final class IrisCompat {
    private static final boolean INSTALLED = Platform.isModLoaded("iris") || Platform.isModLoaded("oculus");
    private static Object api, capturedState;
    private static Method enabled, shadow, reverse, projection, modelView;
    private static boolean compositing;
    private static boolean warned;
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();

    static {
        if (INSTALLED) {
            for (String prefix : new String[] {"net.irisshaders.iris", "net.coderbot.iris"}) {
                try {
                    Class<?> type = Class.forName(prefix + ".api.v0.IrisApi");
                    api = type.getMethod("getInstance").invoke(null);
                    enabled = type.getMethod("isShaderPackInUse");
                    shadow = type.getMethod("isRenderingShadowPass");
                    try { reverse = type.getMethod("isReverseZDuringShaders"); }
                    catch (NoSuchMethodException ignored) {}
                    Class<?> state = Class.forName(prefix + ".uniforms.CapturedRenderingState");
                    capturedState = state.getField("INSTANCE").get(null);
                    projection = state.getMethod("getGbufferProjection");
                    modelView = state.getMethod("getGbufferModelView");
                    break;
                } catch (ReflectiveOperationException | LinkageError ignored) {}
            }
        }
    }

    private IrisCompat() {}

    private static boolean query(Method method, boolean fallback) {
        if (method == null) return fallback;
        try { return (Boolean) method.invoke(api); }
        catch (ReflectiveOperationException ignored) { return fallback; }
    }

    public static boolean shadersActive() { return INSTALLED && query(enabled, true); }
    public static boolean shadowPass() { return INSTALLED && query(shadow, false); }
    public static boolean reverseDepth() { return query(reverse, false); }
    public static boolean compositing() { return compositing; }
    public static Matrix4f projection(Matrix4f out) { return out.set(PROJECTION); }
    public static Matrix4f view(Matrix4f out) { return out.set(VIEW); }

    public static boolean beginComposite() {
        if (!shadersActive() || shadowPass() || compositing) return false;
        try {
            if (projection == null || modelView == null) throw new IllegalStateException("Missing Iris camera access");
            PROJECTION.set((Matrix4fc) projection.invoke(capturedState));
            VIEW.set((Matrix4fc) modelView.invoke(capturedState));
            compositing = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (!warned) {
                warned = true;
                LoggerFactory.getLogger("Amnetic/Iris").warn("Cannot read shader-pack camera; skipping light composite", failure);
            }
            return false;
        }
    }

    public static void endComposite() { compositing = false; }
}
