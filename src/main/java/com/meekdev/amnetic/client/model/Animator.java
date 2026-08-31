package com.meekdev.amnetic.client.model;

import java.util.Arrays;
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

    private Vector3f[] overrideT;
    private Quaternionf[] overrideR;
    private Vector3f[] overrideS;
    private boolean overridden;

    private Vector3f[] spinAxis;
    private float[] spinDegrees;
    private boolean spun;

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

    public int boneCount() {
        return ir.nodes().size();
    }

    public String boneName(int node) {
        return node < 0 || node >= ir.nodes().size() ? null : ir.nodes().get(node).name;
    }

    public int boneIndex(String name) {
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < ir.nodes().size(); i++) {
            if (name.equals(ir.nodes().get(i).name)) {
                return i;
            }
        }
        return -1;
    }

    public int boneParent(int node) {
        return node < 0 || node >= ir.nodes().size() ? -1 : ir.nodes().get(node).parent;
    }

    public Vector3f restTranslation(int node, Vector3f out) {
        return node < 0 || node >= ir.nodes().size()
                ? out.set(0f, 0f, 0f)
                : out.set(ir.nodes().get(node).t);
    }

    public Vector3f restScale(int node, Vector3f out) {
        return node < 0 || node >= ir.nodes().size()
                ? out.set(1f, 1f, 1f)
                : out.set(ir.nodes().get(node).s);
    }

    public Vector3f restRotationDegrees(int node, Vector3f out) {
        if (node < 0 || node >= ir.nodes().size()) {
            return out.set(0f, 0f, 0f);
        }
        ir.nodes().get(node).r.getEulerAnglesXYZ(out);
        return out.set((float) Math.toDegrees(out.x), (float) Math.toDegrees(out.y),
                (float) Math.toDegrees(out.z));
    }

    public Matrix4f worldPose(int node) {
        Matrix4f[] poses = pose();
        return node < 0 || node >= poses.length ? new Matrix4f() : new Matrix4f(poses[node]);
    }

    public Animator overrideTranslation(int node, float x, float y, float z) {
        if (!allocateOverrides(node)) {
            return this;
        }
        if (overrideT[node] == null) {
            overrideT[node] = new Vector3f();
        }
        overrideT[node].set(x, y, z);
        return this;
    }

    public Animator overrideRotation(int node, Quaternionf rotation) {
        if (!allocateOverrides(node) || rotation == null) {
            return this;
        }
        if (overrideR[node] == null) {
            overrideR[node] = new Quaternionf();
        }
        overrideR[node].set(rotation);
        return this;
    }

    public Animator overrideRotationDegrees(int node, float x, float y, float z) {
        return overrideRotation(node, new Quaternionf().rotationXYZ(
                (float) Math.toRadians(x), (float) Math.toRadians(y), (float) Math.toRadians(z)));
    }

    public Animator overrideScale(int node, float x, float y, float z) {
        if (!allocateOverrides(node)) {
            return this;
        }
        if (overrideS[node] == null) {
            overrideS[node] = new Vector3f();
        }
        overrideS[node].set(x, y, z);
        return this;
    }

    public Animator clearOverride(int node) {
        if (!overridden || node < 0 || node >= ir.nodes().size()) {
            return this;
        }
        overrideT[node] = null;
        overrideR[node] = null;
        overrideS[node] = null;
        return this;
    }

    public Animator clearOverrides() {
        overridden = false;
        overrideT = null;
        overrideR = null;
        overrideS = null;
        return this;
    }

    public boolean hasOverrides() {
        return overridden;
    }

    private boolean allocateOverrides(int node) {
        if (node < 0 || node >= ir.nodes().size()) {
            return false;
        }
        if (!overridden) {
            overridden = true;
            overrideT = new Vector3f[ir.nodes().size()];
            overrideR = new Quaternionf[ir.nodes().size()];
            overrideS = new Vector3f[ir.nodes().size()];
        }
        return true;
    }

    public Animator spin(int node, float axisX, float axisY, float axisZ, float degrees) {
        if (node < 0 || node >= ir.nodes().size()) {
            return this;
        }
        float length = (float) Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
        if (length < 1.0e-6f) {
            return this;
        }
        if (spinAxis == null) {
            spinAxis = new Vector3f[ir.nodes().size()];
            spinDegrees = new float[ir.nodes().size()];
        }
        if (spinAxis[node] == null) {
            spinAxis[node] = new Vector3f();
        }
        spinAxis[node].set(axisX / length, axisY / length, axisZ / length);
        spinDegrees[node] = degrees;
        spun = true;
        return this;
    }

    public Animator clearSpin(int node) {
        if (spinAxis != null && node >= 0 && node < spinAxis.length) {
            spinAxis[node] = null;
            spun = false;
            for (Vector3f axis : spinAxis) {
                if (axis != null) {
                    spun = true;
                    break;
                }
            }
        }
        return this;
    }

    public Animator clearSpins() {
        if (spinAxis != null) {
            Arrays.fill(spinAxis, null);
        }
        spun = false;
        return this;
    }

    public boolean hasSpins() {
        return spun;
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

        if (overridden) {
            for (int i = 0; i < nodeTranslation.length; i++) {
                if (overrideT[i] != null) {
                    nodeTranslation[i].set(overrideT[i]);
                }
                if (overrideR[i] != null) {
                    nodeRotation[i].set(overrideR[i]);
                }
                if (overrideS[i] != null) {
                    nodeScale[i].set(overrideS[i]);
                }
            }
        }

        if (spun) {
            for (int i = 0; i < nodeRotation.length; i++) {
                Vector3f axis = spinAxis[i];
                if (axis != null) {
                    nodeRotation[i].rotateAxis((float) Math.toRadians(spinDegrees[i]),
                            axis.x, axis.y, axis.z);
                }
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
        if (ch.interp == ModelIR.Interp.CATMULLROM && n > 2) {
            int p0 = Math.max(0, i - 1) * 3;
            int p3 = Math.min(n - 1, i + 2) * 3;
            out.set(
                    catmullRom(ch.values[p0], ch.values[a], ch.values[b], ch.values[p3], f),
                    catmullRom(ch.values[p0 + 1], ch.values[a + 1], ch.values[b + 1], ch.values[p3 + 1], f),
                    catmullRom(ch.values[p0 + 2], ch.values[a + 2], ch.values[b + 2], ch.values[p3 + 2], f));
            return;
        }
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
        float f = keyFactor(ch.times, i, t);
        if (ch.interp == ModelIR.Interp.CATMULLROM && n > 2) {
            int p0 = Math.max(0, i - 1) * 4;
            int p3 = Math.min(n - 1, i + 2) * 4;
            float[] v = ch.values;
            float s0 = hemisphere(v, p0, v, a);
            float s2 = hemisphere(v, b, v, a);
            float s3 = hemisphere(v, p3, v, a);
            out.set(
                    catmullRom(v[p0] * s0, v[a], v[b] * s2, v[p3] * s3, f),
                    catmullRom(v[p0 + 1] * s0, v[a + 1], v[b + 1] * s2, v[p3 + 1] * s3, f),
                    catmullRom(v[p0 + 2] * s0, v[a + 2], v[b + 2] * s2, v[p3 + 2] * s3, f),
                    catmullRom(v[p0 + 3] * s0, v[a + 3], v[b + 3] * s2, v[p3 + 3] * s3, f));
            if (out.lengthSquared() < 1.0e-8f) {
                out.set(v[a], v[a + 1], v[a + 2], v[a + 3]);
            } else {
                out.normalize();
            }
            return;
        }
        out.slerp(tmpQuatB, f);
    }

    private static float hemisphere(float[] values, int offset, float[] reference, int at) {
        float dot = values[offset] * reference[at]
                + values[offset + 1] * reference[at + 1]
                + values[offset + 2] * reference[at + 2]
                + values[offset + 3] * reference[at + 3];
        return dot < 0f ? -1f : 1f;
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float f) {
        float f2 = f * f;
        float f3 = f2 * f;
        return 0.5f * ((2f * p1)
                + (-p0 + p2) * f
                + (2f * p0 - 5f * p1 + 4f * p2 - p3) * f2
                + (-p0 + 3f * p1 - 3f * p2 + p3) * f3);
    }

    private int findKey(float[] times, float t) {
        int last = times.length - 2;
        if (t <= times[0]) return 0;
        if (t >= times[last + 1]) return last;
        int lo = 0, hi = last;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (times[mid] <= t) lo = mid; else hi = mid - 1;
        }
        return lo;
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
