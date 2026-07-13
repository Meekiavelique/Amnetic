package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Reactive;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// generic 3d view: orbit camera as plain signals (drive them with springs, tweens or
// drag), a frame hook that renders whatever you want to a texture, and the texture shown
// in the widget rect - ModelViewport is the one-model preset, scenes/items/portals are
// the same widget with a different hook
public class Viewport extends Widget {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Surface");

    // orbit state, public signals so anything can read or drive them
    public final Signal<Float> yaw;
    public final Signal<Float> pitch;
    public final Signal<Float> zoom;

    // renders to a texture using the current orbit values, runs once per drawn frame
    public interface FrameHook {
        // returns the gl texture id to show, 0 shows nothing this frame
        int frame(Viewport vp, float dt);
    }

    FrameHook hook;
    boolean orbitInput = true;
    float pitchMin = -1.4f, pitchMax = 1.4f;
    float zoomMin = 0.5f, zoomMax = 10f;
    float dragSensitivity = 0.6f;
    int lastTexture;
    private float lastMx, lastMy;
    private float lastFrameClock = -1;

    public Viewport(float initialYaw, float initialPitch, float initialZoom) {
        yaw = new Signal<>(initialYaw);
        pitch = new Signal<>(initialPitch);
        zoom = new Signal<>(initialZoom);
    }

    public Viewport onFrame(FrameHook hook) { this.hook = hook; return this; }
    public Viewport orbitInput(boolean enabled) { orbitInput = enabled; return this; }
    public Viewport pitchLimits(float min, float max) { pitchMin = min; pitchMax = max; return this; }
    public Viewport zoomLimits(float min, float max) { zoomMin = min; zoomMax = max; return this; }
    public Viewport dragSensitivity(float degPerPx) { dragSensitivity = degPerPx; return this; }

    @Override
    protected boolean interactive() { return orbitInput; }

    @Override
    protected float contentWidth() { return 100; }

    @Override
    protected float contentHeight(float forWidth) { return 100; }

    private float zeroSince = -1;
    private boolean reported;

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        if (hook != null) {
            float now = Reactive.clock().peek();
            float dt = lastFrameClock < 0 ? 0f : now - lastFrameClock;
            lastFrameClock = now;
            d.interrupt(() -> lastTexture = hook.frame(this, dt));

            // diagnostics: a viewport stuck on texture 0 is a content problem (model not
            // ready, render failing), say so once instead of showing nothing silently
            if (lastTexture == 0) {
                if (zeroSince < 0) zeroSince = now;
                if (!reported && now - zeroSince > 2f) {
                    reported = true;
                    LOG.warn("viewport produced no texture for 2s, its content never became ready or its render fails");
                }
            } else if (zeroSince >= 0) {
                zeroSince = -1;
                if (reported) {
                    reported = false;
                    LOG.info("viewport recovered, texture {}", lastTexture);
                }
            }
        }
        if (lastTexture != 0) {
            // offscreen targets are bottom-up, draw flipped
            d.image(lastTexture, x, y + h, w, -h, fade(0xFFFFFFFF, alpha));
        }
    }

    @Override
    public boolean onMouseDown(float mx, float my, int button) {
        lastMx = mx; lastMy = my;
        return true;
    }

    @Override
    public void onMouseDrag(float mx, float my) {
        yaw.update(v -> v + (mx - lastMx) * dragSensitivity);
        pitch.update(v -> clamp((float) (v + (my - lastMy) * dragSensitivity * 0.017), pitchMin, pitchMax));
        lastMx = mx; lastMy = my;
    }

    @Override
    public boolean onScroll(float amount) {
        zoom.update(v -> clamp(v - amount * 0.4f, zoomMin, zoomMax));
        return true;
    }

    private static float clamp(float v, float min, float max) {
        return Math.min(Math.max(v, min), max);
    }

    // fluent overrides so chains keep the subtype
    @Override public Viewport size(float w, float h) { super.size(w, h); return this; }
    @Override public Viewport width(float w) { super.width(w); return this; }
    @Override public Viewport height(float h) { super.height(h); return this; }
    @Override public Viewport grow(float g) { super.grow(g); return this; }
    @Override public Viewport anchor(Anchor a) { super.anchor(a); return this; }
    @Override public Viewport offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public Viewport padding(float p) { super.padding(p); return this; }
    @Override public Viewport visible(boolean v) { super.visible(v); return this; }
    @Override public Viewport opacity(float o) { super.opacity(o); return this; }
}
