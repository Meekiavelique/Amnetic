package com.meekdev.amnetic.client.model;

import java.util.HashMap;
import java.util.Map;

public final class ModelLook {
    private Model.Piece piece;
    private final MaterialLook all = new MaterialLook();
    private final Map<String, MaterialLook> named = new HashMap<>();
    private final MaterialLook merged = new MaterialLook();

    public ModelLook reset() {
        piece = null;
        all.reset();
        for (MaterialLook look : named.values()) look.reset();
        return this;
    }

    public ModelLook piece(Model.Piece piece) {
        this.piece = piece;
        return this;
    }

    public Model.Piece piece() {
        return piece;
    }

    public MaterialLook all() {
        return all;
    }

    public MaterialLook material(String name) {
        return named.computeIfAbsent(name, ignored -> new MaterialLook());
    }

    public boolean emits() {
        if (all.emits()) return true;
        for (MaterialLook look : named.values()) {
            if (look.emits()) return true;
        }
        return false;
    }

    public boolean sameAs(ModelLook other) {
        if (other == this) return true;
        if (other == null || piece != other.piece || !all.sameAs(other.all)) return false;
        return namedSame(named, other.named) && namedSame(other.named, named);
    }

    private static boolean namedSame(Map<String, MaterialLook> mine, Map<String, MaterialLook> theirs) {
        for (Map.Entry<String, MaterialLook> entry : mine.entrySet()) {
            MaterialLook other = theirs.get(entry.getKey());
            if (!entry.getValue().sameAs(other == null ? PLAIN : other)) return false;
        }
        return true;
    }

    private static final MaterialLook PLAIN = new MaterialLook();

    public int contentHash() {
        int h = System.identityHashCode(piece) * 31 + all.contentHash();
        for (Map.Entry<String, MaterialLook> entry : named.entrySet()) {
            int own = entry.getValue().contentHash();
            if (own != PLAIN_HASH) h += entry.getKey().hashCode() ^ own;
        }
        return h;
    }

    private static final int PLAIN_HASH = PLAIN.contentHash();

    public MaterialLook resolve(String name) {
        merged.reset();
        all.onto(merged);
        MaterialLook own = named.get(name);
        if (own != null) own.onto(merged);
        return merged;
    }
}
