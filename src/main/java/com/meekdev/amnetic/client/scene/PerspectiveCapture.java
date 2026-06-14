package com.meekdev.amnetic.client.scene;

import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import com.meekdev.amnetic.client.scene.internal.CaptureTarget;
import net.minecraft.resources.Identifier;

public final class PerspectiveCapture {

    @FunctionalInterface
    public interface CaptureFunction {
        CaptureResult capture(CaptureContext ctx, PerspectiveView view);
    }

    private final Identifier id;
    private final float resolution;
    private final CaptureFunction callback;
    private final PerspectiveView view = new PerspectiveView();
    private final CaptureTarget target = new CaptureTarget();

    private PerspectiveCapture(Identifier id, float resolution, CaptureFunction callback) {
        this.id = id;
        this.resolution = resolution;
        this.callback = callback;
    }

    public static Builder builder() { return new Builder(); }

    public Identifier id() { return id; }
    public float resolution() { return resolution; }
    public PerspectiveView view() { return view; }
    public CaptureTarget target() { return target; }

    public CaptureResult requestView(CaptureContext ctx) {
        view.reset();
        return callback.capture(ctx, view);
    }

    public static final class Builder {
        private float resolution = 0.5f;
        private CaptureFunction callback;

        private Builder() {}

        public Builder resolution(float scale) {
            if (scale <= 0f) throw new IllegalArgumentException("resolution must be > 0, got " + scale);
            this.resolution = scale;
            return this;
        }

        public Builder onCapture(CaptureFunction callback) {
            this.callback = callback;
            return this;
        }

        public PerspectiveCapture register(Identifier id) {
            if (callback == null) throw new IllegalStateException("onCapture(...) is required");
            PerspectiveCapture capture = new PerspectiveCapture(id, resolution, callback);
            CaptureManager.INSTANCE.register(capture);
            return capture;
        }
    }
}
