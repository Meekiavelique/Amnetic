package com.meekdev.amnetic.client.model;

import com.meekdev.amnetic.client.material.SurfaceInputs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

// draws of one model that stay where they are, handed in once and drawn every frame until cleared
// for things that do not move: nothing about them is worked out again each frame but culling and level of detail
// a look or inputs handed in are still read when the frame is drawn, so the caller may keep changing them
public final class KeptDraws {

    // a draw's light is sampled again this many frames apart, a few of them each frame
    private static final int RELIGHT_FRAMES = 64;

    private final Model model;
    private final List<Model.Draw> draws = new ArrayList<>();
    // the box each draw fills in the world, min then max
    private float[] boxes = new float[0];
    // block light and the sky light before the time of day darkens it, NaN until sampled
    private float[] light = new float[0];
    private int[] levels = new int[0];
    private int relight;
    private int version;

    KeptDraws(Model model) {
        this.model = model;
    }

    public void add(Matrix4fc world, SurfaceInputs inputs, ModelLook look, Object key, float lodPixels) {
        int at = draws.size();
        Matrix4f placed = new Matrix4f(world);
        draws.add(new Model.Draw(placed, null, Float.NaN, Float.NaN, Float.NaN, inputs, look, key, lodPixels));
        if (boxes.length < (at + 1) * 6) {
            int room = Math.max(16, (at + 1) * 2);
            boxes = Arrays.copyOf(boxes, room * 6);
            light = Arrays.copyOf(light, room * 2);
            levels = Arrays.copyOf(levels, room);
        }
        Model.Piece piece = look == null ? null : look.piece();
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        if (piece != null) {
            min.set(piece.min());
            max.set(piece.max());
            new Matrix4f(placed).mul(piece.frame()).transformAab(min, max, min, max);
        } else {
            model.localBounds(min, max);
            placed.transformAab(min, max, min, max);
        }
        boxes[at * 6] = min.x;
        boxes[at * 6 + 1] = min.y;
        boxes[at * 6 + 2] = min.z;
        boxes[at * 6 + 3] = max.x;
        boxes[at * 6 + 4] = max.y;
        boxes[at * 6 + 5] = max.z;
        light[at * 2] = Float.NaN;
        light[at * 2 + 1] = Float.NaN;
        levels[at] = -1;
        version++;
    }

    public void clear() {
        draws.clear();
        version++;
    }

    public int size() {
        return draws.size();
    }

    // goes up whenever a draw is added or they are cleared
    public int version() {
        return version;
    }

    public Model.Draw internalDraw(int index) {
        return draws.get(index);
    }

    public List<Model.Draw> internalDraws() {
        return draws;
    }

    public float[] internalBoxes() {
        return boxes;
    }

    public float[] internalLight() {
        return light;
    }

    public int[] internalLevels() {
        return levels;
    }

    // which draws sample their light again this frame, a slice at a time
    public boolean internalRelight(int index) {
        return Float.isNaN(light[index * 2]) || index % RELIGHT_FRAMES == relight;
    }

    public void internalNextFrame() {
        relight = (relight + 1) % RELIGHT_FRAMES;
    }
}
