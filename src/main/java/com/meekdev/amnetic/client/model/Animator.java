package com.meekdev.amnetic.client.model;

import com.meekdev.amnetic.client.model.internal.ModelIR;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class Animator {

    private final Model model;
    private final ModelIR ir;

    private final Vector3f[] nodeTranslation;
    private final Quaternionf[] nodeRotation;
    private final Vector3f[] nodeScale;
    private final Matrix4f[] localPose;
    private final Matrix4f[] worldPose;

    private final Vector3f tmpVec = new Vector3f();
    private final Quaternionf tmpQuat = new Quaternionf();
    private final Quaternionf tmpQuatB = new Quaternionf();

    private String current;
    private String crossfadeTo;
    private float crossfadeDuration;
    private float crossfadeElapsed;
    private boolean loop = true;
    private float speed = 1f;
    private float time;

    Animator(Model model, ModelIR ir) {
        this.model = model;
        this.ir = ir;
        int count = ir.nodes().size();
        this.nodeTranslation = new Vector3f[count];
        this.nodeRotation = new Quaternionf[count];
        this.nodeScale = new Vector3f[count];
        this.localPose = new Matrix4f[count];
        this.worldPose = new Matrix4f[count];
        for (int i = 0; i < count; i++) {
            nodeTranslation[i] = new Vector3f();
            nodeRotation[i] = new Quaternionf();
            nodeScale[i] = new Vector3f();
            localPose[i] = new Matrix4f();
            worldPose[i] = new Matrix4f();
        }
    }

    public Animator play(String clip) {
        this.current = clip;
        this.crossfadeTo = null;
        this.time = 0f;
        return this;
    }

    public Animator loop(boolean loop) {
        this.loop = loop;
        return this;
    }

    public Animator speed(float speed) {
        this.speed = speed;
        return this;
    }

    public Animator setTime(float seconds) {
        this.time = Math.max(0f, seconds);
        return this;
    }

    public Animator crossfade(String from, String clip, float seconds) {
        this.current = from;
        this.crossfadeTo = clip;
        this.crossfadeDuration = Math.max(1e-3f, seconds);
        this.crossfadeElapsed = 0f;
        return this;
    }

    public Animator update(float dt) {
        time += dt * speed;
        if (crossfadeTo != null) {
            crossfadeElapsed += dt;
            if (crossfadeElapsed >= crossfadeDuration) {
                current = crossfadeTo;
                crossfadeTo = null;
                time = 0f;
            }
        }
        return this;
    }

    public Matrix4f[] pose() {
        for (int i = 0; i < nodeTranslation.length; i++) {
            ModelIR.Node node = ir.nodes().get(i);
            nodeTranslation[i].set(node.t);
            nodeRotation[i].set(node.r);
            nodeScale[i].set(node.s);
        }

        ModelIR.Animation base = current == null ? null : ir.animation(current);
        if (base != null) {
            sample(base, time);
        }
        if (crossfadeTo != null) {
            ModelIR.Animation target = ir.animation(crossfadeTo);
            if (target != null) {
                float blend = Math.min(1f, crossfadeElapsed / crossfadeDuration);
                blendInto(target, crossfadeElapsed, blend);
            }
        }

        for (int i = 0; i < localPose.length; i++) {
            localPose[i].translationRotateScale(nodeTranslation[i], nodeRotation[i], nodeScale[i]);
        }
        for (int i = 0; i < worldPose.length; i++) {
            ModelIR.Node node = ir.nodes().get(i);
            if (node.parent < 0) {
                worldPose[i].set(localPose[i]);
            } else {
                worldPose[node.parent].mul(localPose[i], worldPose[i]);
            }
        }
        return worldPose;
    }

    public Model model() {
        return model;
    }

    public String current() {
        return current;
    }

    public boolean isLooping() {
        return loop;
    }

    public float speedValue() {
        return speed;
    }

    public float time() {
        return time;
    }

    public void dispose() {
    }

    private void sample(ModelIR.Animation anim, float clock) {
        float t = clipTime(anim, clock);
        for (ModelIR.Channel ch : anim.channels) {
            applyChannel(ch, t, 1f);
        }
    }

    private void blendInto(ModelIR.Animation anim, float clock, float weight) {
        float t = clipTime(anim, clock);
        for (ModelIR.Channel ch : anim.channels) {
            applyChannel(ch, t, weight);
        }
    }

    private float clipTime(ModelIR.Animation anim, float clock) {
        if (anim.duration <= 0f) {
            return 0f;
        }
        if (loop) {
            return clock % anim.duration;
        }
        return Math.min(clock, anim.duration);
    }

    private void applyChannel(ModelIR.Channel ch, float t, float weight) {
        switch (ch.path) {
            case TRANSLATION -> {
                sampleVec(ch, t, tmpVec);
                nodeTranslation[ch.node].lerp(tmpVec, weight);
            }
            case SCALE -> {
                sampleVec(ch, t, tmpVec);
                nodeScale[ch.node].lerp(tmpVec, weight);
            }
            case ROTATION -> {
                sampleQuat(ch, t, tmpQuat);
                nodeRotation[ch.node].slerp(tmpQuat, weight);
            }
        }
    }

    private void sampleVec(ModelIR.Channel ch, float t, Vector3f out) {
        int n = ch.times.length;
        if (n == 1) {
            out.set(ch.values[0], ch.values[1], ch.values[2]);
            return;
        }
        int i = findKey(ch.times, t);
        int a = i * 3;
        int b = (i + 1) * 3;
        if (ch.interp == ModelIR.Interp.STEP) {
            out.set(ch.values[a], ch.values[a + 1], ch.values[a + 2]);
            return;
        }
        float f = keyFactor(ch.times, i, t);
        out.set(
                ch.values[a] + (ch.values[b] - ch.values[a]) * f,
                ch.values[a + 1] + (ch.values[b + 1] - ch.values[a + 1]) * f,
                ch.values[a + 2] + (ch.values[b + 2] - ch.values[a + 2]) * f);
    }

    private void sampleQuat(ModelIR.Channel ch, float t, Quaternionf out) {
        int n = ch.times.length;
        if (n == 1) {
            out.set(ch.values[0], ch.values[1], ch.values[2], ch.values[3]);
            return;
        }
        int i = findKey(ch.times, t);
        int a = i * 4;
        int b = (i + 1) * 4;
        out.set(ch.values[a], ch.values[a + 1], ch.values[a + 2], ch.values[a + 3]);
        if (ch.interp == ModelIR.Interp.STEP) {
            return;
        }
        tmpQuatB.set(ch.values[b], ch.values[b + 1], ch.values[b + 2], ch.values[b + 3]);
        out.slerp(tmpQuatB, keyFactor(ch.times, i, t));
    }

    private int findKey(float[] times, float t) {
        for (int i = 0; i < times.length - 1; i++) {
            if (t < times[i + 1]) {
                return i;
            }
        }
        return times.length - 2;
    }

    private float keyFactor(float[] times, int i, float t) {
        float span = times[i + 1] - times[i];
        if (span <= 1e-8f) {
            return 0f;
        }
        float f = (t - times[i]) / span;
        return f < 0f ? 0f : (f > 1f ? 1f : f);
    }
}
