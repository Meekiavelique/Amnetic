package com.meekdev.amnetic.client.post;

import com.meekdev.amnetic.client.post.internal.PostEffectEntry;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.util.Identifier;
import org.joml.*;

import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class PostEffectConfig {

    private final PostEffectEntry entry;

    PostEffectConfig(PostEffectEntry entry) {
        this.entry = entry;
    }

    public PostEffectConfig when(BooleanSupplier condition) {
        entry.setCondition(condition);
        return this;
    }

    public PostEffectConfig priority(int priority) {
        entry.setPriority(priority);
        return this;
    }

    public PostEffectConfig phase(RenderPhase phase) {
        entry.setPhase(phase);
        return this;
    }

    public PostEffectConfig externalTargets(Set<Identifier> targets) {
        entry.setExternalTargets(targets);
        return this;
    }

    public PostEffectConfig externalTargets(Identifier... targets) {
        entry.setExternalTargets(Set.of(targets));
        return this;
    }

    public PostEffectConfig externalTarget(Identifier id, Supplier<Framebuffer> supplier) {
        entry.putExternalTargetSupplier(id, supplier);
        return this;
    }

    public PostEffectConfig fadeIn(int ticks) {
        entry.setFadeIn(ticks);
        return this;
    }

    public PostEffectConfig fadeOut(int ticks) {
        entry.setFadeOut(ticks);
        return this;
    }

    public PostEffectConfig fade(int inTicks, int outTicks) {
        entry.setFadeIn(inTicks);
        entry.setFadeOut(outTicks);
        return this;
    }

    public PostEffectConfig onBeforeApply(Consumer<PostEffectContext> callback) {
        entry.setOnBeforeApply(callback);
        return this;
    }

    public PostEffectConfig onAfterApply(Consumer<PostEffectContext> callback) {
        entry.setOnAfterApply(callback);
        return this;
    }

    public PostEffectConfig uniform(String name, float value) {
        entry.putUniformSlot(name, UniformSuppliers.constant(value));
        return this;
    }

    public PostEffectConfig uniform(String name, float x, float y) {
        entry.putUniformSlot(name, UniformSuppliers.constant(x, y));
        return this;
    }

    public PostEffectConfig uniform(String name, float x, float y, float z) {
        Vector3f vec = new Vector3f(x, y, z);
        entry.putUniformSlot(name, () -> List.of(new UniformValue.Vec3fValue(vec)));
        return this;
    }

    public PostEffectConfig uniform(String name, float x, float y, float z, float w) {
        Vector4f vec = new Vector4f(x, y, z, w);
        entry.putUniformSlot(name, () -> List.of(new UniformValue.Vec4fValue(vec)));
        return this;
    }

    public PostEffectConfig uniform(String name, int value) {
        entry.putUniformSlot(name, () -> List.of(new UniformValue.IntValue(value)));
        return this;
    }

    public PostEffectConfig uniform(String name, DoubleSupplier supplier) {
        entry.putUniformSlot(name, UniformSuppliers.ofFloat(supplier));
        return this;
    }

    public PostEffectConfig uniform(String name, IntSupplier supplier) {
        entry.putUniformSlot(name, UniformSuppliers.ofInt(supplier));
        return this;
    }

    public PostEffectConfig uniformVec2(String name, Supplier<Vector2fc> supplier) {
        entry.putUniformSlot(name, UniformSuppliers.ofVec2(supplier));
        return this;
    }

    public PostEffectConfig uniformVec3(String name, Supplier<Vector3fc> supplier) {
        entry.putUniformSlot(name, UniformSuppliers.ofVec3(supplier));
        return this;
    }

    public PostEffectConfig uniformVec4(String name, Supplier<Vector4fc> supplier) {
        entry.putUniformSlot(name, UniformSuppliers.ofVec4(supplier));
        return this;
    }

    public PostEffectConfig uniformMat4(String name, Supplier<Matrix4fc> supplier) {
        entry.putUniformSlot(name, UniformSuppliers.ofMat4(supplier));
        return this;
    }

    public PostEffectConfig uniformRaw(String name, Supplier<List<UniformValue>> supplier) {
        entry.putUniformSlot(name, supplier);
        return this;
    }

    public PostEffectConfig texture(String samplerName, Identifier textureId) {
        entry.putTextureOverride(samplerName, textureId);
        return this;
    }
}
