package com.meekdev.amnetic.client.gbuffer;

import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** remembers where each key was drawn last frame, so a shader can write how far it moved */
public final class PreviousTransforms<K> {

    private static final class Seen {
        final Matrix4f last = new Matrix4f();
        final Matrix4f now = new Matrix4f();
        long frame;
    }

    private final Map<K, Seen> seen = new WeakHashMap<>();

    /** the transform the key had last frame, or current when it was not drawn then */
    public Matrix4fc of(K key, Matrix4fc current) {
        long frame = GBuffer.frame();
        Seen s = seen.get(key);
        if (s == null) {
            s = new Seen();
            s.last.set(current);
            s.now.set(current);
            s.frame = frame;
            seen.put(key, s);
            return s.last;
        }
        if (s.frame != frame) {
            s.last.set(s.frame == frame - 1 ? s.now : current);
            s.frame = frame;
        }
        s.now.set(current);
        return s.last;
    }

    public void clear() {
        seen.clear();
    }
}
