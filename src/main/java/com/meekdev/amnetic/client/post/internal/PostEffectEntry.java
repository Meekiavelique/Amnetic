package com.meekdev.amnetic.client.post.internal;

import com.google.gson.JsonParser;
import com.meekdev.amnetic.client.post.PostEffectContext;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.meekdev.amnetic.mixin.accessor.ShaderLoaderAccessor;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PostEffectEntry {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/PostEffect");

    private final Identifier id;
    private Set<Identifier> externalTargets;
    private BooleanSupplier condition;
    private int priority;
    private RenderPhase phase;
    private final Map<String, Supplier<List<UniformValue>>> uniformSlots;
    private final Map<String, Identifier> textureOverrides;
    private Consumer<PostEffectContext> onBeforeApply;
    private Consumer<PostEffectContext> onAfterApply;
    private int fadeInTicks;
    private int fadeOutTicks;

    private boolean enabled = true;
    private boolean active = false;

    private float intensity = 1f;
    private boolean wasConditionMet = false;

    private PostEffectPipeline cachedBasePipeline;
    private PostEffectProcessor ownedProcessor;
    private final UniformBufferWriter uniformBufferWriter = new UniformBufferWriter();
    private List<Identifier> lastTextureSnapshot;

    PostEffectEntry(Identifier id) {
        this.id = id;
        this.externalTargets = Set.of(PostEffectProcessor.MAIN);
        this.condition = () -> true;
        this.priority = 0;
        this.phase = RenderPhase.POST_WORLD;
        this.uniformSlots = new LinkedHashMap<>();
        this.textureOverrides = new LinkedHashMap<>();
        this.fadeInTicks = 0;
        this.fadeOutTicks = 0;
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
    }

    public void setFadeOut(int ticks) {
        this.fadeOutTicks = ticks;
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
        cachedBasePipeline = null;
        closeOwned();
    }

    public void apply(RenderPhase currentPhase, float deltaTick, ObjectAllocator allocator) {
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
            closeOwned();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        PostEffectProcessor processor = resolveProcessor(mc);
        if (processor == null) {
            active = false;
            return;
        }

        active = true;
        wasConditionMet = conditionMet;

        PostEffectContext ctx = new PostEffectContext(
                mc, deltaTick,
                mc.getWindow().getFramebufferWidth(),
                mc.getWindow().getFramebufferHeight(),
                processor
        );

        if (onBeforeApply != null) onBeforeApply.accept(ctx);
        processor.render(mc.getFramebuffer(), allocator);
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

    private PostEffectProcessor resolveProcessor(MinecraftClient mc) {
        boolean needsOwnInstance = !uniformSlots.isEmpty() || !textureOverrides.isEmpty() || hasFade();

        if (!needsOwnInstance) {
            return mc.getShaderLoader().loadPostEffect(id, externalTargets);
        }

        if (ownedProcessor == null || isTextureDirty()) {
            closeOwned();
            PostEffectPipeline base = getOrLoadBasePipeline(mc);
            if (base == null) return null;

            Map<String, Supplier<List<UniformValue>>> slotsForBuild = new LinkedHashMap<>(uniformSlots);
            if (hasFade()) {
                float capturedIntensity = this.intensity;
                slotsForBuild.put("Intensity", () -> List.of(new UniformValue.FloatValue(capturedIntensity)));
            }

            PostEffectPipeline modified = PipelineBuilder.build(base, slotsForBuild, textureOverrides);
            ShaderLoader shaderLoader = mc.getShaderLoader();
            ProjectionMatrix2 projMatrix = ((ShaderLoaderAccessor) shaderLoader).amnetic$getProjectionMatrix();

            try {
                ownedProcessor = PostEffectProcessor.parseEffect(modified, mc.getTextureManager(), externalTargets, id, projMatrix);
                lastTextureSnapshot = new ArrayList<>(textureOverrides.values());
            } catch (ShaderLoader.LoadException e) {
                LOGGER.error("Failed to build post effect processor for {}: {}", id, e.getMessage());
                return null;
            }
        }

        Map<String, List<UniformValue>> effectiveUniforms = buildEffectiveUniforms();
        uniformBufferWriter.update(ownedProcessor, effectiveUniforms);

        return ownedProcessor;
    }

    private Map<String, List<UniformValue>> buildEffectiveUniforms() {
        Map<String, List<UniformValue>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Supplier<List<UniformValue>>> e : uniformSlots.entrySet()) {
            result.put(e.getKey(), e.getValue().get());
        }
        if (hasFade()) {
            result.put("Intensity", List.of(new UniformValue.FloatValue(intensity)));
        }
        return result;
    }

    private boolean hasFade() {
        return fadeInTicks > 0 || fadeOutTicks > 0;
    }

    private boolean isTextureDirty() {
        return !new ArrayList<>(textureOverrides.values()).equals(lastTextureSnapshot);
    }

    private PostEffectPipeline getOrLoadBasePipeline(MinecraftClient mc) {
        if (cachedBasePipeline != null) return cachedBasePipeline;

        Identifier resourceId = Identifier.of(id.getNamespace(), "post_effect/" + id.getPath() + ".json");
        Optional<Resource> resource = mc.getResourceManager().getResource(resourceId);
        if (resource.isEmpty()) {
            LOGGER.warn("Post effect resource not found: {}", resourceId);
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(resource.get().getInputStream())) {
            cachedBasePipeline = PostEffectPipeline.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow();
            return cachedBasePipeline;
        } catch (Exception e) {
            LOGGER.error("Failed to load post effect pipeline for {}: {}", resourceId, e.getMessage());
            return null;
        }
    }

    private void handleDeactivation() {
        if (fadeOutTicks > 0 && intensity > 0f) {
            wasConditionMet = false;
        } else {
            active = false;
            intensity = 0f;
            closeOwned();
        }
    }

    public void close() {
        closeOwned();
        uniformBufferWriter.close();
        cachedBasePipeline = null;
    }

    private void closeOwned() {
        if (ownedProcessor != null) {
            ownedProcessor.close();
            ownedProcessor = null;
            lastTextureSnapshot = null;
        }
    }
}
