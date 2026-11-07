package com.meekdev.amnetic.client.render;

import com.meekdev.amnetic.client.compat.IrisCompat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public abstract class ScreenPass {

    protected final Logger log;
    private final FailureGuard guard;
    private ShaderProgram program;

    private static final Set<ScreenPass> ALL = Collections.newSetFromMap(new WeakHashMap<>());

    protected ScreenPass(String name) {
        this.log = LoggerFactory.getLogger("Amnetic/" + name);
        this.guard = new FailureGuard(name, 60);
        ALL.add(this);
    }

    // builds the program objects of every pass made so far, the actual links happen in ShaderProgram.warm
    public static void prepareAll() {
        for (ScreenPass pass : new ArrayList<>(ALL)) {
            if (pass.program != null) continue;
            try {
                pass.program = pass.createProgram();
            } catch (RuntimeException ignored) {
            }
        }
    }

    protected abstract ShaderProgram createProgram();

    protected abstract boolean record(CameraSnapshot cam, ShaderProgram program);

    protected boolean enabled() { return true; }

    protected boolean skipUnderIris() { return true; }

    protected final boolean alive() { return guard.alive(); }


    protected final void dispatchNative(Runnable render) {
        if (!guard.alive() || !enabled()) return;
        if (skipUnderIris() && IrisCompat.shadersActive()) return;
        if (CameraSnapshot.current() == null) return;
        try {
            render.run();
            guard.success();
        } catch (Throwable failure) { guard.fail(log, failure); }
    }

    protected final void dispatch() {
        if (!guard.alive() || !enabled()) return;
        if (IrisCompat.shadowPass()) return;
        if (skipUnderIris() && IrisCompat.shadersActive()) return;
        CameraSnapshot cam = CameraSnapshot.current();
        if (cam == null) return;
        if (program == null) program = createProgram();

        try {
            GlState.beginFullscreen();
            try {
                record(cam, program);
            } finally {
                GlState.endFullscreen();
            }
            guard.success();
        } catch (Throwable e) {
            guard.fail(log, e);
        }
    }

    public void dispose() {
        if (program != null) { program.close(); program = null; }
        onDispose();
    }

    protected void onDispose() {}
}
