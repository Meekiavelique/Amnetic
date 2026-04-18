package com.meekdev.amnetic.client.post;

import com.meekdev.amnetic.client.post.internal.PostEffectEntry;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
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

public final class PostEffectHandle {

    private final PostEffectEntry entry;

    PostEffectHandle(PostEffectEntry entry) {
        this.entry = entry;
    }

    public PostEffectHandle enable() {
        entry.setEnabled(true);
        return this;
    }

    public PostEffectHandle disable() {
        entry.setEnabled(false);
        return this;
    }

    public PostEffectHandle setEnabled(boolean enabled) {
        entry.setEnabled(enabled);
        return this;
    }

    public PostEffectHandle setCondition(BooleanSupplier condition) {
        entry.setCondition(condition);
        return this;
    }

    public PostEffectHandle setPriority(int priority) {
        entry.setPriority(priority);
        return this;
    }

    public PostEffectHandle setPhase(RenderPhase phase) {
        entry.setPhase(phase);
        return this;
    }

    public PostEffectHandle setExternalTargets(Set<Identifier> targets) {
        entry.setExternalTargets(targets);
        return this;
    }

    public PostEffectHandle setExternalTarget(Identifier id, Supplier<Framebuffer> supplier) {
        entry.putExternalTargetSupplier(id, supplier);
        return this;
    }

    public PostEffectHandle setFadeIn(int ticks) {
        entry.setFadeIn(ticks);
        return this;
    }

    public PostEffectHandle setFadeOut(int ticks) {
        entry.setFadeOut(ticks);
        return this;
    }

    public PostEffectHandle setFade(int inTicks, int outTicks) {
        entry.setFadeIn(inTicks);
        entry.setFadeOut(outTicks);
        return this;
    }

    public PostEffectHandle onBeforeApply(Consumer<PostEffectContext> callback) {
        entry.setOnBeforeApply(callback);
        return this;
    }

    public PostEffectHandle onAfterApply(Consumer<PostEffectContext> callback) {
        entry.setOnAfterApply(callback);
        return this;
    }

    public PostEffectHandle uniform(String name, float value) {
        entry.putUniformSlot(name, UniformSuppliers.constant(value));
        return this;
    }

    public PostEffectHandle uniform(String name, float x, float y) {
        entry.putUniformSlot(name, UniformSuppliers.constant(x, y));
        return this;
    }

    public PostEffectHandle uniform(String name, float x, float y, float z) {
        Vector3f vec = new Vector3f(x, y, z);
        entry.putUniformSlot(name, () -> List.of(new UniformValue.Vec3fValue(vec)));
        return this;
    }

    public PostEffectHandle uniform(String name, float x, float y, float z, float w) {
        Vector4f vec = new Vector4f(x, y, z, w);
        entry.putUniformSlot(name, () -> List.of(new UniformValue.Vec4fValue(vec)));
        return this;
    }

    public PostEffectHandle uniform(String name, int value) {
        entry.putUniformSlot(name, () -> List.of(new UniformValue.IntValue(value)));
        return this;
    }

    public PostEffectHandle uniform(String name, DoubleSupplier supplier) {
        entry.putUniformSlot(name, UniformSuppliers.ofFloat(supplier));
        return this;
    }

    public PostEffectHandle uniform(String name, IntSupplier supplier) {
        entry.putUniformSlot(name, UniformSuppliers.ofInt(supplier));
        return this;
    }

    public PostEffectHandle uniformVec2(String name, Supplier<Vector2fc> supplier) {
        entry.putUniformSlot(name, UniformSuppliers.ofVec2(supplier));
        return this;
    }

    public PostEffectHandle uniformVec3(String name, Supplier<Vector3fc> supplier) {
        entry.putUniformSlot(name, UniformSuppliers.ofVec3(supplier));
        return this;
    }

    public PostEffectHandle uniformVec4(String name, Supplier<Vector4fc> supplier) {
        entry.putUniformSlot(name, UniformSuppliers.ofVec4(supplier));
        return this;
    }

    public PostEffectHandle uniformMat4(String name, Supplier<Matrix4fc> supplier) {
        entry.putUniformSlot(name, UniformSuppliers.ofMat4(supplier));
        return this;
    }

    public PostEffectHandle uniformRaw(String name, Supplier<List<UniformValue>> supplier) {
        entry.putUniformSlot(name, supplier);
        return this;
    }

    public PostEffectHandle texture(String samplerName, Identifier textureId) {
        entry.putTextureOverride(samplerName, textureId);
        return this;
    }

    public boolean isActive() {
        return entry.isActive();
    }

    public boolean isEnabled() {
        return entry.isEnabled();
    }

    public Identifier getId() {
        return entry.getId();
    }

    public void unregister() {
        PostEffectRegistry.INSTANCE.unregister(entry);
    }
}
