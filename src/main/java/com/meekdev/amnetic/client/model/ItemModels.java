package com.meekdev.amnetic.client.model;

import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.resources.Identifier;

public final class ItemModels {

    private static final CopyOnWriteArrayList<Binding> BINDINGS = new CopyOnWriteArrayList<>();
    private static boolean hooked;

    private ItemModels() {
    }

    public static Binding bind(Identifier textureId, ModelView view) {
        Binding binding = new Binding(textureId, view);
        add(binding);
        return binding;
    }

    public static void clear() {
        for (Binding binding : BINDINGS) {
            binding.view.dispose();
        }
        BINDINGS.clear();
    }

    private static synchronized void add(Binding binding) {
        ensureHooked();
        BINDINGS.add(binding);
    }

    private static void ensureHooked() {
        if (hooked) {
            return;
        }
        hooked = true;
        Models.onFrame(ctx -> {
            float dt = ctx.deltaTick();
            for (Binding binding : BINDINGS) {
                binding.tick(dt);
            }
        });
    }

    public static final class Binding {
        private final Identifier textureId;
        private final ModelView view;
        private float spinSpeed = 45f;
        private boolean spin = true;
        private boolean removed;

        private Binding(Identifier textureId, ModelView view) {
            this.textureId = textureId;
            this.view = view;
        }

        public Binding spin(float degreesPerSecond) {
            this.spinSpeed = degreesPerSecond;
            this.spin = degreesPerSecond != 0f;
            return this;
        }

        public Binding still() {
            this.spin = false;
            return this;
        }

        public ModelView view() {
            return view;
        }

        public void remove() {
            if (removed) {
                return;
            }
            removed = true;
            BINDINGS.remove(this);
            view.dispose();
        }

        private void tick(float dt) {
            if (removed) {
                return;
            }
            if (spin) {
                view.spin(spinSpeed, dt);
            }
            view.update(dt);
            view.render();
            view.register(textureId);
        }
    }
}
