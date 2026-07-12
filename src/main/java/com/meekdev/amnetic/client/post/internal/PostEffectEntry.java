package com.meekdev.amnetic.client.post.internal;

import com.google.gson.JsonParser;
import com.meekdev.amnetic.client.post.PostEffectContext;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.mixin.accessor.ShaderLoaderAccessor;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.serialization.JsonOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

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

    private PostChainConfig cachedBasePipeline;
    private PostChain ownedProcessor;
    private final UniformBufferWriter uniformBufferWriter = new UniformBufferWriter();
    private List<Identifier> lastTextureSnapshot;

    PostEffectEntry(Identifier id) {
        this.id = normalizePostEffectId(id);
        this.pipelineResourcePath = Identifier.fromNamespaceAndPath(this.id.getNamespace(), "post_effect/" + this.id.getPath() + ".json");
        this.externalTargets = Set.of(PostChain.MAIN_TARGET_ID);
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

    public void apply(RenderPhase currentPhase, float deltaTick, GraphicsResourceAllocator allocator) {
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

        Minecraft mc = Minecraft.getInstance();
        Set<Identifier> effectiveExternalTargets = getEffectiveExternalTargets(mc);
        PostChain processor = resolveProcessor(mc, effectiveExternalTargets);
        if (processor == null) {
            active = false;
            return;
        }

        active = true;
        wasConditionMet = conditionMet;

        PostEffectContext ctx = new PostEffectContext(
                mc, deltaTick,
                mc.getWindow().getWidth(),
                mc.getWindow().getHeight(),
                processor
        );

        if (onBeforeApply != null) onBeforeApply.accept(ctx);
        renderProcessor(mc, processor, allocator, effectiveExternalTargets);
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

    private PostChain resolveProcessor(Minecraft mc, Set<Identifier> effectiveExternalTargets) {
        boolean needsOwnInstance = !uniformSlots.isEmpty() || !textureOverrides.isEmpty() || hasFade();

        if (!needsOwnInstance) {
            return mc.getShaderManager().getPostChain(id, effectiveExternalTargets);
        }

        if (ownedProcessor == null || isTextureDirty()) {
            closeOwned();
            PostChainConfig base = getOrLoadBasePipeline(mc);
            if (base == null) return null;

            Map<String, Supplier<List<UniformValue>>> slotsForBuild = new LinkedHashMap<>(uniformSlots);
            if (hasFade()) {
                float capturedIntensity = this.intensity;
                slotsForBuild.put("Intensity", () -> List.of(new UniformValue.FloatUniform(capturedIntensity)));
            }

            PostChainConfig modified = PipelineBuilder.build(base, slotsForBuild, textureOverrides);
            ShaderManager shaderLoader = mc.getShaderManager();
            Projection projection = ((ShaderLoaderAccessor) shaderLoader).amnetic$getProjection();
            ProjectionMatrixBuffer projMatrix = ((ShaderLoaderAccessor) shaderLoader).amnetic$getProjectionMatrixBuffer();

            try {
                ownedProcessor = PostChain.load(modified, mc.getTextureManager(), effectiveExternalTargets, id, projection, projMatrix);
                lastTextureSnapshot = new ArrayList<>(textureOverrides.values());
            } catch (ShaderManager.CompilationException e) {
                LOGGER.error("Failed to build post effect processor for {}: {}", id, e.getMessage());
                return null;
            }
        }

        Map<String, List<UniformValue>> effectiveUniforms = buildEffectiveUniforms();
        uniformBufferWriter.update(ownedProcessor, effectiveUniforms);

        return ownedProcessor;
    }

    private Set<Identifier> getEffectiveExternalTargets(Minecraft mc) {
        Set<Identifier> effective = new LinkedHashSet<>(externalTargets);
        PostChainConfig base = getOrLoadBasePipeline(mc);
        if (base != null && pipelineUsesTarget(base, WorldDepthSnapshot.TARGET_ID)) {
            effective.add(WorldDepthSnapshot.TARGET_ID);
        }
        return Set.copyOf(effective);
    }

    private static boolean pipelineUsesTarget(PostChainConfig pipeline, Identifier targetId) {
        for (PostChainConfig.Pass pass : pipeline.passes()) {
            for (PostChainConfig.Input input : pass.inputs()) {
                if (input instanceof PostChainConfig.TargetInput sampler && sampler.targetId().equals(targetId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void renderProcessor(Minecraft mc, PostChain processor, GraphicsResourceAllocator allocator, Set<Identifier> effectiveExternalTargets) {
        RenderTarget mainFramebuffer = mc.getMainRenderTarget();
        // a PostChain mutates raw GL (blend, bound FBO, program, textures) through the GpuDevice without
        // updating the GlStateManager cache. reset to a known-clean state afterwards so the deferred lights,
        // volumetrics and particles later this frame aren't corrupted by leftover state
        try {
            if (effectiveExternalTargets.equals(Set.of(PostChain.MAIN_TARGET_ID))) {
                processor.process(mainFramebuffer, allocator);
                return;
            }

            FrameGraphBuilder frameGraph = new FrameGraphBuilder();
            MapFramebufferSet framebufferSet = new MapFramebufferSet();
            framebufferSet.replace(PostChain.MAIN_TARGET_ID, frameGraph.importExternal("minecraft:main", mainFramebuffer));

            for (Identifier targetId : effectiveExternalTargets) {
                if (targetId.equals(PostChain.MAIN_TARGET_ID)) continue;

                RenderTarget framebuffer = resolveExternalFramebuffer(mc, targetId);
                if (framebuffer == null) {
                    LOGGER.warn("Skipping post effect {} because external target {} is unavailable", id, targetId);
                    return;
                }

                framebufferSet.replace(targetId, frameGraph.importExternal(targetId.toString(), framebuffer));
            }

            processor.addToFrame(frameGraph, mainFramebuffer.width, mainFramebuffer.height, framebufferSet);
            frameGraph.execute(allocator);
        } finally {
            GlState.endFullscreen();
        }
    }

    private RenderTarget resolveExternalFramebuffer(Minecraft mc, Identifier targetId) {
        Supplier<RenderTarget> customTarget = externalTargetSuppliers.get(targetId);
        if (customTarget != null) {
            return customTarget.get();
        }
        if (targetId.equals(PostChain.MAIN_TARGET_ID)) {
            return mc.getMainRenderTarget();
        }
        if (targetId.equals(LevelTargetBundle.TRANSLUCENT_TARGET_ID)) {
            return mc.levelRenderer.getTranslucentTarget();
        }
        if (targetId.equals(LevelTargetBundle.ITEM_ENTITY_TARGET_ID)) {
            return mc.levelRenderer.getItemEntityTarget();
        }
        if (targetId.equals(LevelTargetBundle.PARTICLES_TARGET_ID)) {
            return mc.levelRenderer.getParticlesTarget();
        }
        if (targetId.equals(LevelTargetBundle.WEATHER_TARGET_ID)) {
            return mc.levelRenderer.getWeatherTarget();
        }
        if (targetId.equals(LevelTargetBundle.CLOUDS_TARGET_ID)) {
            return mc.levelRenderer.getCloudsTarget();
        }
        if (targetId.equals(LevelTargetBundle.ENTITY_OUTLINE_TARGET_ID)) {
            return mc.levelRenderer.entityOutlineTarget();
        }
        if (targetId.equals(WorldDepthSnapshot.TARGET_ID)) {
            return WorldDepthSnapshot.getFramebuffer();
        }
        return null;
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

    private boolean isTextureDirty() {
        return !new ArrayList<>(textureOverrides.values()).equals(lastTextureSnapshot);
    }

    private PostChainConfig getOrLoadBasePipeline(Minecraft mc) {
        if (cachedBasePipeline != null) return cachedBasePipeline;

        Optional<Resource> resource = mc.getResourceManager().getResource(pipelineResourcePath);
        if (resource.isEmpty()) {
            LOGGER.warn("Post effect resource not found: {}", pipelineResourcePath);
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(resource.get().open())) {
            cachedBasePipeline = PostChainConfig.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow();
            return cachedBasePipeline;
        } catch (Exception e) {
            LOGGER.error("Failed to load post effect pipeline for {}: {}", pipelineResourcePath, e.getMessage());
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

    private static final class MapFramebufferSet implements PostChain.TargetBundle {

        private final Map<Identifier, ResourceHandle<RenderTarget>> handles = new HashMap<>();

        @Override
        public void replace(Identifier id, ResourceHandle<RenderTarget> handle) {
            handles.put(id, handle);
        }

        @Override
        public ResourceHandle<RenderTarget> get(Identifier id) {
            return handles.get(id);
        }
    }
}
