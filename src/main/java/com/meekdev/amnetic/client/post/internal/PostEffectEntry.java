package com.meekdev.amnetic.client.post.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.post.PostEffectContext;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.meekdev.amnetic.client.post.UniformValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class PostEffectEntry {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/PostEffect");

    private final Identifier id;
    private final Identifier pipelineResourcePath;
    private Set<Identifier> externalTargets;
    private BooleanSupplier condition;
    private int priority;
    private RenderPhase phase;
    private final Map<String, Supplier<List<UniformValue>>> uniformSlots;
    private final Map<String, Identifier> textureOverrides;
    private final Map<Identifier, Supplier<RenderTarget>> externalTargetSuppliers;
    private Consumer<PostEffectContext> onBeforeApply;
    private Consumer<PostEffectContext> onAfterApply;
    private int fadeInTicks;
    private int fadeOutTicks;

    private boolean enabled = true;
    private boolean active = false;

    private float intensity = 1f;
    private boolean wasConditionMet = false;

    private PostPipeline pipeline;
    private boolean pipelineFailed;

    PostEffectEntry(Identifier id) {
        this.id = normalizePostEffectId(id);
        this.pipelineResourcePath = Identifier.fromNamespaceAndPath(this.id.getNamespace(), "post_effect/" + this.id.getPath() + ".json");
        this.externalTargets = Set.of(PostPipeline.MAIN);
        this.condition = () -> true;
        this.priority = 0;
        this.phase = RenderPhase.POST_WORLD;
        this.uniformSlots = new LinkedHashMap<>();
        this.textureOverrides = new LinkedHashMap<>();
        this.externalTargetSuppliers = new LinkedHashMap<>();
        this.fadeInTicks = 0;
        this.fadeOutTicks = 0;
    }

    private static Identifier normalizePostEffectId(Identifier id) {
        String path = id.getPath();
        if (path.endsWith(".json")) path = path.substring(0, path.length() - ".json".length());
        if (path.startsWith("post_effect/")) path = path.substring("post_effect/".length());
        return Identifier.fromNamespaceAndPath(id.getNamespace(), path);
    }

    public void setCondition(BooleanSupplier condition) {
        this.condition = Objects.requireNonNull(condition);
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setPhase(RenderPhase phase) {
        this.phase = Objects.requireNonNull(phase);
    }

    public void setExternalTargets(Set<Identifier> targets) {
        this.externalTargets = Set.copyOf(targets);
    }

    public void setFadeIn(int ticks) {
        this.fadeInTicks = ticks;
        // intensity starts at 1 so unfaded effects work, but a fade-in configured while
        // inactive should ramp from black on first activation instead of popping in
        if (ticks > 0 && !active && !wasConditionMet) intensity = 0f;
    }

    public void setFadeOut(int ticks) {
        this.fadeOutTicks = ticks;
    }

    public void putExternalTargetSupplier(Identifier id, Supplier<RenderTarget> supplier) {
        this.externalTargetSuppliers.put(id, supplier);
        LinkedHashSet<Identifier> updatedTargets = new LinkedHashSet<>(this.externalTargets);
        updatedTargets.add(id);
        this.externalTargets = Set.copyOf(updatedTargets);
    }

    public void setOnBeforeApply(Consumer<PostEffectContext> callback) {
        this.onBeforeApply = callback;
    }

    public void setOnAfterApply(Consumer<PostEffectContext> callback) {
        this.onAfterApply = callback;
    }

    public void putUniformSlot(String name, Supplier<List<UniformValue>> supplier) {
        uniformSlots.put(name, supplier);
    }

    public void putTextureOverride(String samplerName, Identifier textureId) {
        textureOverrides.put(samplerName, textureId);
        invalidatePipelineCache();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isActive() {
        return active;
    }

    public int getPriority() {
        return priority;
    }

    public RenderPhase getPhase() {
        return phase;
    }

    public Identifier getId() {
        return id;
    }

    public void invalidatePipelineCache() {
        closePipeline();
        pipelineFailed = false;
    }

    public void apply(RenderPhase currentPhase, float deltaTick) {
        if (this.phase != currentPhase) return;
        if (!enabled) {
            handleDeactivation();
            return;
        }

        boolean conditionMet = condition.getAsBoolean();

        if (!conditionMet && !wasConditionMet) {
            active = false;
            return;
        }

        updateIntensity(conditionMet, deltaTick);

        if (intensity <= 0f) {
            active = false;
            wasConditionMet = false;
            return;
        }

        PostPipeline pipeline = resolvePipeline();
        if (pipeline == null) {
            active = false;
            return;
        }

        active = true;
        wasConditionMet = conditionMet;

        Minecraft mc = Minecraft.getInstance();
        PostEffectContext ctx = new PostEffectContext(
                mc, deltaTick,
                mc.getWindow().getWidth(),
                mc.getWindow().getHeight()
        );

        if (onBeforeApply != null) onBeforeApply.accept(ctx);
        try {
            pipeline.run(buildEffectiveUniforms(), this::resolveExternalTarget);
        } catch (RuntimeException e) {
            LOGGER.error("Post effect {} failed, disabling it until resources reload: {}", id, e.getMessage());
            pipelineFailed = true;
            closePipeline();
            active = false;
            return;
        }
        if (onAfterApply != null) onAfterApply.accept(ctx);
    }

    private void updateIntensity(boolean conditionMet, float deltaTick) {
        if (conditionMet) {
            if (fadeInTicks > 0) {
                intensity = Math.min(1f, intensity + deltaTick / fadeInTicks);
            } else {
                intensity = 1f;
            }
        } else {
            if (fadeOutTicks > 0) {
                intensity = Math.max(0f, intensity - deltaTick / fadeOutTicks);
            } else {
                intensity = 0f;
            }
        }
    }

    private PostPipeline resolvePipeline() {
        if (pipeline != null) return pipeline;
        if (pipelineFailed) return null;
        try {
            pipeline = PostPipeline.load(id, pipelineResourcePath, textureOverrides);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to load post effect pipeline for {}: {}", pipelineResourcePath, e.getMessage());
            pipelineFailed = true;
            return null;
        }
        return pipeline;
    }

    private RenderTarget resolveExternalTarget(Identifier targetId) {
        Supplier<RenderTarget> customTarget = externalTargetSuppliers.get(targetId);
        if (customTarget != null) {
            return customTarget.get();
        }
        if (targetId.equals(WorldDepthSnapshot.TARGET_ID)) {
            return WorldDepthSnapshot.getFramebuffer();
        }
        return VanillaCompat.levelTarget(targetId);
    }

    private Map<String, List<UniformValue>> buildEffectiveUniforms() {
        Map<String, List<UniformValue>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Supplier<List<UniformValue>>> e : uniformSlots.entrySet()) {
            result.put(e.getKey(), e.getValue().get());
        }
        if (hasFade()) {
            result.put("Intensity", List.of(new UniformValue.FloatUniform(intensity)));
        }
        return result;
    }

    private boolean hasFade() {
        return fadeInTicks > 0 || fadeOutTicks > 0;
    }

    private void handleDeactivation() {
        if (fadeOutTicks > 0 && intensity > 0f) {
            wasConditionMet = false;
        } else {
            active = false;
            intensity = 0f;
        }
    }

    public void close() {
        closePipeline();
    }

    private void closePipeline() {
        if (pipeline != null) {
            pipeline.close();
            pipeline = null;
        }
    }
}
