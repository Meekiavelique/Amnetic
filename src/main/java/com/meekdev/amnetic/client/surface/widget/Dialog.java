package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Fx;
import com.meekdev.amnetic.client.surface.ScreenSurface;
import java.util.function.Consumer;

public final class Dialog {

    private Dialog() {}

    public static void confirm(ScreenSurface surface, String title, String message,
                               Runnable onYes, Runnable onNo) {
        Dim dim = new Dim(onNo);
        Panel card = card();
        Column col = new Column().gap(10);
        col.add(new Text(title).px(16));
        col.add(new Text(message).px(13).color(0xC0FFFFFF).wrap(true));
        Row actions = new Row().gap(8);
        actions.add(new Stack().grow(1));
        actions.add(new Button("No").onClick(() -> {
            close(dim);
            if (onNo != null) onNo.run();
        }));
        actions.add(new Button("Yes").colors(0xFF4C8FDD, 0xFF5F9FE8).onClick(() -> {
            close(dim);
            if (onYes != null) onYes.run();
        }));
        col.add(actions);
        card.add(col);
        dim.add(card);
        surface.overlay().add(dim);
        pop(dim, card);
    }

    public static void prompt(ScreenSurface surface, String title, String placeholder,
                              Consumer<String> onSubmit) {
        Dim dim = new Dim(null);
        Panel card = card();
        Column col = new Column().gap(10);
        col.add(new Text(title).px(16));
        TextField field = new TextField("").placeholder(placeholder);
        Runnable submit = () -> {
            close(dim);
            if (onSubmit != null) onSubmit.accept(field.value.peek());
        };
        field.onSubmit(s -> submit.run());
        col.add(field);
        Row actions = new Row().gap(8);
        actions.add(new Stack().grow(1));
        actions.add(new Button("Cancel").onClick(() -> close(dim)));
        actions.add(new Button("OK").colors(0xFF4C8FDD, 0xFF5F9FE8).onClick(submit));
        col.add(actions);
        card.add(col);
        dim.add(card);
        surface.overlay().add(dim);
        pop(dim, card);
        surface.internalInput().setFocus(field);
    }

    private static Panel card() {
        return new Card().width(300).padding(16).rounding(12)
                .background(0xF0161B23).border(1f, 0x30FFFFFF).shadow(18)
                .anchor(Anchor.CENTER);
    }

    private static void pop(Widget dim, Widget card) {
        Fx.fadeIn(dim, 0.15f);
        card.scaleChannel(0.9f);
        Fx.spring(card::scaleChannel, 0.9f, 60f, 9f).target(1f);
    }

    private static void close(Widget dim) {
        Fx.fadeOut(dim, 0.12f);
    }

    private static final class Dim extends Panel {

        private final Runnable onDismiss;
        private boolean closing;

        Dim(Runnable onDismiss) {
            this.onDismiss = onDismiss;
            background(0x80000000);
            rounding(0);
        }

        @Override
        protected boolean interactive() { return true; }

        @Override
        public void onMouseUp(float mx, float my, int button) {
            if (closing) return;
            closing = true;
            close(this);
            if (onDismiss != null) onDismiss.run();
        }
    }

    private static final class Card extends Panel {

        @Override
        protected boolean interactive() { return true; }
    }
}
