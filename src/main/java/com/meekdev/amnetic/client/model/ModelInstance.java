package com.meekdev.amnetic.client.model;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class ModelInstance {

    private final Model model;
    private final Animator animator;
    private final Matrix4f transform = new Matrix4f();
    private boolean disposed;

    public ModelInstance(Model model) {
        this.model = model;
        this.animator = model.createAnimator();
    }

    public Model model() {
        return model;
    }

    public Animator animator() {
        return animator;
    }

    public ModelInstance setTransform(Matrix4fc t) {
        transform.set(t);
        return this;
    }

    public Matrix4f transform() {
        return transform;
    }

    public ModelInstance update(float dt) {
        animator.update(dt);
        return this;
    }

    public ModelInstance render() {
        if (disposed) {
            return this;
        }
        if (model.isAnimated() && animator.current() != null) {
            Matrix4f[] pose = animator.pose();
            Matrix4f[] copy = new Matrix4f[pose.length];
            for (int i = 0; i < pose.length; i++) {
                copy[i] = new Matrix4f(pose[i]);
            }
            model.renderPosed(transform, copy);
        } else {
            model.render(transform);
        }
        return this;
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        animator.dispose();
    }
}
