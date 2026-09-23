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

    public MaterialLook resolve(String name) {
        merged.reset();
        all.onto(merged);
        MaterialLook own = named.get(name);
        if (own != null) own.onto(merged);
        return merged;
    }
}
