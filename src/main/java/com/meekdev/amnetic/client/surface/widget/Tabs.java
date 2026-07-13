package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Effect;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.amnetic.client.surface.text.SdfFont;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;

// tab bar over a content stack, the underline rides animateLayout so it glides
// between tabs and a width spring keeps it hugging the selected label
public class Tabs extends Column {

    public final Signal<Integer> selected = new Signal<>(0);
    Consumer<Integer> onChange;
    int accent = 0xFF4C8FDD;
    float px = 13;

    private final Row header = new Row().gap(2);
    private final Panel underline = new Panel().background(0xFF4C8FDD).rounding(1.5f);
    private final Stack content = new Stack();
    private final List<TabButton> buttons = new ArrayList<>();
    private final List<Widget> pages = new ArrayList<>();
    private final Motion<Float> underW = Motion.spring(1f, 60f, 9f);
    private final Effect sync;

    public Tabs() {
        gap(8);
        underline.animateLayout(true);
        // custom header: the row lays out normally, then the underline lands under
        // the selected tab and springs there on change
        Stack headerStack = new Stack() {
            @Override
            protected float contentWidth() {
                return header.measureWidth();
            }

            @Override
            protected float contentHeight(float forWidth) {
                return header.measureHeight(forWidth) + 5;
            }

            @Override
            protected void placeChildren() {
                float hh = header.measureHeight(w);
                header.layout(x, y, w, hh);
                int sel = index();
                if (sel >= 0) {
                    TabButton b = buttons.get(sel);
                    underW.target(Math.max(b.w, 1f));
                    underline.layout(b.x, y + hh + 2, Math.max(underW.value().peek(), 1f), 3);
                }
            }
        };
        headerStack.add(header);
        headerStack.add(underline);
        add(headerStack);
        add(content.grow(1));
        sync = new Effect(() -> {
            int sel = clampIndex(selected.get());
            for (int i = 0; i < pages.size(); i++) pages.get(i).visible(i == sel);
        });
    }

    public Tabs onChange(Consumer<Integer> c) { onChange = c; return this; }
    public Tabs accent(int argb) { accent = argb; underline.background(argb); return this; }
    public Tabs px(float p) { px = p; return this; }

    public Tabs addTab(String title, Widget page) {
        TabButton b = new TabButton(title, buttons.size());
        buttons.add(b);
        header.add(b);
        pages.add(page);
        page.visible(pages.size() - 1 == index());
        content.add(page);
        return this;
    }

    public void select(int index) {
        index = clampIndex(index);
        if (selected.peek() == index) return;
        selected.set(index);
        if (onChange != null) onChange.accept(index);
    }

    private int clampIndex(int i) {
        return pages.isEmpty() ? 0 : Math.min(Math.max(i, 0), pages.size() - 1);
    }

    private int index() {
        return buttons.isEmpty() ? -1 : clampIndex(selected.peek());
    }

    @Override
    public void remove() {
        sync.dispose();
        underW.dispose();
        super.remove();
    }

    // flat header button, hover pill on a spring, selection tint snaps because
    // the gliding underline already carries the transition
    private class TabButton extends Widget {

        final String title;
        final int tabIndex;
        private final Signal<Float> hoverTarget = new Signal<>(0f);
        private final Motion<Float> hoverT = Motion.spring(0f, 60f, 9f);

        TabButton(String title, int tabIndex) {
            this.title = title;
            this.tabIndex = tabIndex;
            hoverT.follow(hoverTarget);
        }

        @Override
        protected boolean interactive() { return true; }

        @Override
        protected float contentWidth() {
            Identifier id = Surfaces.defaultFont();
            SdfFont f = id == null ? null : Fonts.get(id);
            return (f == null ? 50 : f.width(title, px)) + 20;
        }

        @Override
        protected float contentHeight(float forWidth) {
            return px + 12;
        }

        @Override
        protected void drawSelf(UiDraw d, float alpha) {
            hoverTarget.set(hovered ? 1f : 0f);
            float t = hoverT.value().peek();
            if (t > 0.01f) d.roundedRect(x, y, w, h, 6, fade(0xFFFFFFFF, 0.08f * t * alpha));
            boolean sel = index() == tabIndex;
            int color = sel ? 0xFFFFFFFF : lerpColor(0x90FFFFFF, 0xD0FFFFFF, t);
            Identifier prev = d.currentFont();
            d.font(Surfaces.defaultFont());
            d.textCentered(title, x + w * 0.5f, y + h * 0.5f, px, fade(color, alpha));
            if (prev != null) d.font(prev);
        }

        @Override
        public boolean onMouseDown(float mx, float my, int button) {
            return true;
        }

        @Override
        public void onMouseUp(float mx, float my, int button) {
            if (mx >= x && my >= y && mx <= x + w && my <= y + h) select(tabIndex);
        }

        @Override
        public void remove() {
            hoverT.dispose();
            super.remove();
        }
    }

    // fluent overrides so chains keep the subtype
    @Override public Tabs gap(float g) { super.gap(g); return this; }
    @Override public Tabs size(float w, float h) { super.size(w, h); return this; }
    @Override public Tabs width(float w) { super.width(w); return this; }
    @Override public Tabs height(float h) { super.height(h); return this; }
    @Override public Tabs grow(float g) { super.grow(g); return this; }
    @Override public Tabs anchor(Anchor a) { super.anchor(a); return this; }
    @Override public Tabs offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public Tabs padding(float p) { super.padding(p); return this; }
    @Override public Tabs visible(boolean v) { super.visible(v); return this; }
    @Override public Tabs opacity(float o) { super.opacity(o); return this; }
}
