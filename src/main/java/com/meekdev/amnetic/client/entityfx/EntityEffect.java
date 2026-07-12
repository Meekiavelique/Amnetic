package com.meekdev.amnetic.client.entityfx;

import com.meekdev.amnetic.client.entityfx.internal.EffectUniforms;
import com.meekdev.amnetic.client.entityfx.internal.EntityEffectPipeline;
import com.meekdev.amnetic.client.entityfx.internal.EntityEffectRegistry;
import com.meekdev.amnetic.client.entityfx.internal.SceneColorSnapshot;
import com.meekdev.amnetic.client.particle.SceneDepth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public final class EntityEffect {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private static final Identifier NO_SKIN = Identifier.fromNamespaceAndPath("amnetic", "entityfx_no_skin");

    private final Entity entity;
    private final Identifier vsh;
    private final Identifier fsh;
    private final boolean replaceBody;
    private final boolean needsSkin;
    private final boolean needsSceneColor;
    private final boolean needsDepth;
    private final Map<String, Identifier> extraSamplers;
    private final EffectUniforms uniforms;
    private final Map<Integer, DoubleSupplier> dynamic = new LinkedHashMap<>();
    private final Map<Identifier, RenderType> bySkin = new HashMap<>();

    private int fadeTotal = -1;
    private int fadeRemaining;
    private int fadeChannel = -1;
    private boolean removed;

    EntityEffect(Entity entity, Identifier vsh, Identifier fsh, boolean replaceBody,
                 boolean needsSkin, boolean needsSceneColor, boolean needsDepth,
                 Map<String, Identifier> extraSamplers) {
        this.entity = entity;
        this.vsh = vsh;
        this.fsh = fsh;
        this.replaceBody = replaceBody;
        this.needsSkin = needsSkin;
        this.needsSceneColor = needsSceneColor;
        this.needsDepth = needsDepth;
        this.extraSamplers = extraSamplers == null ? Collections.emptyMap() : Map.copyOf(extraSamplers);
        int seq = SEQ.getAndIncrement();
        this.uniforms = new EffectUniforms(
                Identifier.fromNamespaceAndPath("amnetic", "entityfx_uniform/" + seq));
    }

    public EntityEffect setUniform(int channel, float value01) {
        dynamic.remove(channel);
        uniforms.set(channel, value01);
        return this;
    }

    public EntityEffect setUniform(int channel, DoubleSupplier supplier) {
        dynamic.put(channel, supplier);
        return this;
    }

    public EntityEffect fadeOutOver(int ticks, int fadeChannel) {
        this.fadeTotal = Math.max(1, ticks);
        this.fadeRemaining = this.fadeTotal;
        this.fadeChannel = fadeChannel;
        return this;
    }

    public boolean replacesBody() {
        return replaceBody;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void remove() {
        if (removed) return;
        removed = true;
        EntityEffectRegistry.INSTANCE.remove(entity);
    }

    public RenderType renderType(Identifier skinId) {
        Identifier key = needsSkin && skinId != null ? skinId : NO_SKIN;
        return bySkin.computeIfAbsent(key, k -> {
            uploadUniforms();
            if (needsSceneColor) SceneColorSnapshot.INSTANCE.ensureRegistered();
            if (needsDepth) SceneDepth.update();
            return EntityEffectPipeline.build(
                    "amnetic_entityfx/" + SEQ.getAndIncrement(), vsh, fsh, uniforms.id(),
                    needsSkin ? skinId : null, needsSceneColor, needsDepth, extraSamplers);
        });
    }

    public void uploadUniforms() {
        for (Map.Entry<Integer, DoubleSupplier> e : dynamic.entrySet()) {
            uniforms.set(e.getKey(), (float) e.getValue().getAsDouble());
        }
        uniforms.upload();
    }

    public boolean tickFade() {
        if (fadeTotal <= 0) return false;
        fadeRemaining--;
        if (fadeChannel >= 0) {
            uniforms.set(fadeChannel, Math.max(0f, (float) fadeRemaining / fadeTotal));
        }
        return fadeRemaining <= 0;
    }

    public void disposeInternal() {
        uniforms.dispose();
    }
}
