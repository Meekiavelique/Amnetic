package com.meekdev.amnetic.client.model;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class ModelInstance {

    private final Model model;
    private final Animator animator;
    private final Matrix4f transform = new Matrix4f();

    public ModelInstance(Model model) {
        this.model = model;
        this.animator = model.createAnimator();
    }

    public Model model() { return model; }
    public Animator animator() { return animator; }

    public ModelInstance setTransform(Matrix4fc t) { transform.set(t); return this; }

    public Matrix4f transform() { return transform; }

    public ModelInstance render() {
        model.render(transform);
        return this;
    }

    public void dispose() {
        animator.dispose();
        model.dispose();
    }
}
