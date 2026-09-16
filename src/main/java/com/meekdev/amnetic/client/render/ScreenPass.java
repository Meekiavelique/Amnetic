package com.meekdev.amnetic.client.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ScreenPass {

    protected final Logger log;
    private final FailureGuard guard;
    private ShaderProgram program;

    protected ScreenPass(String name) {
        this.log = LoggerFactory.getLogger("Amnetic/" + name);
        this.guard = new FailureGuard(name, 60);
    }

    protected abstract ShaderProgram createProgram();

    protected abstract boolean record(CameraSnapshot cam, ShaderProgram program);

    protected boolean enabled() { return true; }

    protected final void dispatch() {
        if (!guard.alive() || !enabled()) return;
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
