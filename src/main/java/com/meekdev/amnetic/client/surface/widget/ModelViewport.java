package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelView;
import com.meekdev.amnetic.client.surface.Anchor;

public class ModelViewport extends Viewport {

    final ModelView view;
    float autoSpin;

    public ModelViewport(Model model, int renderSize) {
        super(30f, 0.35f, 2.2f);
        view = new ModelView(renderSize, renderSize).model(model);
        onFrame((vp, dt) -> {
            if (autoSpin != 0) yaw.update(v -> v + autoSpin * dt);
            view.yaw(yaw.peek())
                .pitch((float) Math.toDegrees(pitch.peek()))
                .zoom(zoom.peek())
                .update(dt)
                .render();
            return view.textureId();
        });
    }

    public ModelViewport autoSpin(float degreesPerSecond) { autoSpin = degreesPerSecond; return this; }
    public ModelViewport play(String clip) { view.play(clip); return this; }
    public ModelViewport light(float block, float sky) { view.light(block, sky); return this; }
    public ModelViewport fov(float degrees) { view.fov(degrees); return this; }

    public ModelView view() { return view; }

    @Override
    public void remove() {
        view.dispose();
        super.remove();
    }

    @Override public ModelViewport size(float w, float h) { super.size(w, h); return this; }
    @Override public ModelViewport width(float w) { super.width(w); return this; }
    @Override public ModelViewport height(float h) { super.height(h); return this; }
    @Override public ModelViewport grow(float g) { super.grow(g); return this; }
    @Override public ModelViewport anchor(Anchor a) { super.anchor(a); return this; }
    @Override public ModelViewport offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public ModelViewport padding(float p) { super.padding(p); return this; }
    @Override public ModelViewport visible(boolean v) { super.visible(v); return this; }
    @Override public ModelViewport opacity(float o) { super.opacity(o); return this; }
}
