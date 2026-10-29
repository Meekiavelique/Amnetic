package com.meekdev.amnetic.client.model;

import java.util.HashMap;
import java.util.Map;

/**
 * what one draw of a model changes: the piece of the file it shows, and its materials, for every
 * material at once and per material name. it travels with the draw, so two draws of the same model
 * in one frame can look different
 */
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

    // whether a draw with this look and one with other come out the same, the same piece and the same changes
    // a material named on one side only counts as the same when it changes nothing
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

    // equal for looks that are sameAs each other
    public int contentHash() {
        int h = System.identityHashCode(piece) * 31 + all.contentHash();
        for (Map.Entry<String, MaterialLook> entry : named.entrySet()) {
            int own = entry.getValue().contentHash();
            if (own != PLAIN_HASH) h += entry.getKey().hashCode() ^ own;
        }
        return h;
    }

    private static final int PLAIN_HASH = PLAIN.contentHash();

    /** the changes that land on the material called name, all-material ones first */
    public MaterialLook resolve(String name) {
        merged.reset();
        all.onto(merged);
        MaterialLook own = named.get(name);
        if (own != null) own.onto(merged);
        return merged;
    }
}
