package com.meekdev.amnetic.client.model;

import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

public final class WorldModels {

    private static final CopyOnWriteArrayList<Placement> PLACEMENTS = new CopyOnWriteArrayList<>();
    private static boolean hooked;

    private WorldModels() {
    }

    public static Placement place(Model model, BlockPos pos) {
        Placement placement = new Placement(model, pos);
        add(placement);
        return placement;
    }

    public static void clear() {
        PLACEMENTS.clear();
    }

    private static synchronized void add(Placement placement) {
        ensureHooked();
        PLACEMENTS.add(placement);
    }

    private static void ensureHooked() {
        if (hooked) {
            return;
        }
        hooked = true;
        Models.onFrame(ctx -> {
            float dt = Minecraft.getInstance().getDeltaTracker().getRealtimeDeltaTicks() / 20f;
            for (Placement placement : PLACEMENTS) {
                placement.submit(dt);
            }
        });
    }

    public static final class Placement {
        private final Model model;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float scale = 1f;
        private boolean removed;
        private Animator animator;

        private Placement(Model model, BlockPos pos) {
            this.model = model;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY();
            this.z = pos.getZ() + 0.5;
        }

        public Placement play(String clip) {
            if (model.isAnimated()) {
                if (animator == null) {
                    animator = model.createAnimator();
                }
                animator.play(clip).loop(true);
            }
            return this;
        }

        public Placement playFirst() {
            return play(model.firstClip());
        }

        public Placement yaw(float degrees) {
            this.yaw = degrees;
            return this;
        }

        public Placement scale(float scale) {
            this.scale = scale;
            return this;
        }

        public Placement offset(double dx, double dy, double dz) {
            this.x += dx;
            this.y += dy;
            this.z += dz;
            return this;
        }

        public void remove() {
            if (removed) {
                return;
            }
            removed = true;
            PLACEMENTS.remove(this);
        }

        private void submit(float dt) {
            if (removed) {
                return;
            }
            Matrix4f world = new Matrix4f()
                    .translation((float) x, (float) y, (float) z)
                    .rotateY((float) Math.toRadians(yaw))
                    .scale(scale);
            if (animator != null && animator.current() != null) {
                animator.update(dt);
                Matrix4f[] pose = animator.pose();
                Matrix4f[] copy = new Matrix4f[pose.length];
                for (int i = 0; i < pose.length; i++) {
                    copy[i] = new Matrix4f(pose[i]);
                }
                model.renderPosed(world, copy);
            } else {
                model.render(world);
            }
        }
    }
}
