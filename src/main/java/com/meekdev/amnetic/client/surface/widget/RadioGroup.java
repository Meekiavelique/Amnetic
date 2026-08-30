package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import java.util.function.Consumer;

public class RadioGroup extends Column {

    public final Signal<Integer> selected;
    Consumer<Integer> onChange;
    private int radioCount;

    public RadioGroup(int initialSelected) {
        selected = new Signal<>(initialSelected);
    }

    public RadioGroup onChange(Consumer<Integer> c) { onChange = c; return this; }

    public RadioGroup option(String label) {
        return add(new RadioButton(label));
    }

    @Override
    public RadioGroup add(Widget child) {
        if (child instanceof RadioButton r) {
            r.group = this;
            r.index = radioCount++;
        }
        super.add(child);
        return this;
    }

    public void select(int index) {
        if (selected.peek() == index) return;
        selected.set(index);
        if (onChange != null) onChange.accept(index);
    }

    @Override public RadioGroup gap(float g) { super.gap(g); return this; }
    @Override public RadioGroup size(float w, float h) { super.size(w, h); return this; }
    @Override public RadioGroup width(float w) { super.width(w); return this; }
    @Override public RadioGroup height(float h) { super.height(h); return this; }
    @Override public RadioGroup grow(float g) { super.grow(g); return this; }
    @Override public RadioGroup anchor(Anchor a) { super.anchor(a); return this; }
    @Override public RadioGroup offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public RadioGroup padding(float p) { super.padding(p); return this; }
    @Override public RadioGroup visible(boolean v) { super.visible(v); return this; }
    @Override public RadioGroup opacity(float o) { super.opacity(o); return this; }
}
