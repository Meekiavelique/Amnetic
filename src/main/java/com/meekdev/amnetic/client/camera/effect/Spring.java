package com.meekdev.amnetic.client.camera.effect;

import com.meekdev.amnetic.client.camera.CameraFrame;
import com.meekdev.amnetic.client.camera.CameraModifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class Spring implements CameraModifier {

    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private final Vector3f target = new Vector3f();
    private float stiffness = 120f;
    private float damping = 18f;

    public Spring to(Vec3 offset, float stiffness, float damping) {
        target.set((float) offset.x, (float) offset.y, (float) offset.z);
        this.stiffness = stiffness;
        this.damping = damping;
        return this;
    }

    @Override
    public void modify(CameraFrame frame) {
        integrate(frame.dt());
        frame.addPosition(position.x, position.y, position.z);
    }

    @Override
    public boolean finished() {
        return target.lengthSquared() < 1.0e-8f
                && position.lengthSquared() < 1.0e-7f
                && velocity.lengthSquared() < 1.0e-6f;
    }

    // substepped so a long frame cannot make the spring explode
    private void integrate(float dt) {
        int steps = Math.max(1, (int) Math.ceil(dt / 0.008f));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            velocity.x += (-stiffness * (position.x - target.x) - damping * velocity.x) * h;
            velocity.y += (-stiffness * (position.y - target.y) - damping * velocity.y) * h;
            velocity.z += (-stiffness * (position.z - target.z) - damping * velocity.z) * h;
            position.fma(h, velocity);
        }
    }
}
