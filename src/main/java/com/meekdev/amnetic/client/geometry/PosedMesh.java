package com.meekdev.amnetic.client.geometry;

import com.meekdev.amnetic.client.geometry.internal.CapturingConsumer;
import com.meekdev.amnetic.client.geometry.internal.MeshTapRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

public final class PosedMesh {

    private final Entity entity;
    private final CapturingConsumer consumer = new CapturingConsumer();
    private volatile boolean captured;
    private boolean removed;

    public PosedMesh(Entity entity) {
        this.entity = entity;
    }

    public Entity entity() {
        return entity;
    }

    public int vertexCount() {
        return captured ? consumer.vertexCount() : 0;
    }

    public float[] positions() {
        return consumer.positions();
    }

    public float[] uvs() {
        return consumer.uvs();
    }

    public float[] normals() {
        return consumer.normals();
    }

    public boolean isRemoved() {
        return removed;
    }

    public void remove() {
        if (removed) return;
        removed = true;
        MeshTapRegistry.INSTANCE.remove(entity);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void captureFrom(Model model, EntityRenderState state, PoseStack poseStack) {
        ((Model) model).setupAnim(state);   // ensure this frame's pose (deferred draw poses at draw time)
        consumer.begin();
        model.renderToBuffer(poseStack, consumer, 0, 0);
        captured = true;
    }
}
