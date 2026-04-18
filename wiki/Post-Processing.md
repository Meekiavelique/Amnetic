# Post-Processing

This page is the complete reference for the post-processing API: `PostEffects`, `PostEffectConfig`, `PostEffectHandle`, `RenderPhase`, priority ordering, and the fade system.

---

## PostEffects (entry point)

`PostEffects` is the main public API class. All methods are static.

### register

Overloads:

```java
PostEffectHandle register(Identifier id)
PostEffectHandle register(Identifier id, BooleanSupplier condition)
PostEffectHandle register(Identifier id, Consumer<PostEffectConfig> configurator)
```

`id` is the pipeline name under `post_effect` (without the `.json` extension). For example, `Identifier.of("mymod", "frost")` loads `assets/mymod/post_effect/frost.json`.

### Convenience methods

```java
PostEffectHandle blur(Identifier id, float radius)
PostEffectHandle blur(Identifier id, DoubleSupplier radius)

PostEffectHandle vignette(Identifier id, float intensity)
PostEffectHandle vignette(Identifier id, DoubleSupplier intensity)

PostEffectHandle conditionalWithFade(Identifier id, BooleanSupplier condition, int fadeInTicks, int fadeOutTicks)
```

These methods register the effect and return a handle. They are equivalent to calling `register` with the appropriate configuration.

---

## PostEffectConfig (builder)

`PostEffectConfig` is the builder passed to the `Consumer<PostEffectConfig>` in `PostEffects.register`. All methods return `this` for chaining.

### Condition

```java
cfg.when(BooleanSupplier condition)
```

The effect is only rendered when the supplier returns `true`. If not set, the effect is always active (`enable`/`disable` on the handle).

### Phase

```java
cfg.phase(RenderPhase phase)
```

Controls where in the frame the effect is applied. Default is `RenderPhase.POST_WORLD`. See [RenderPhase](#renderphase) below.

### Priority

```java
cfg.priority(int priority)
```

Higher values are applied first. Default is `0`. See [Priority ordering](#priority-ordering) below.

### External targets

```java
cfg.externalTargets(Set<Identifier> targets)
cfg.externalTarget(Identifier id, Supplier<Framebuffer> supplier)
```

The set of named framebuffer targets that the pipeline reads from or writes to outside of its own declared targets. By default this is `Set.of(PostEffectProcessor.MAIN)`, which represents the main scene framebuffer (`minecraft:main`). You should not need to change this unless your effect uses additional shared targets.

`externalTarget(...)` binds a concrete framebuffer supplier to an external target id so the runtime can actually provide that target during rendering.

### Fade

```java
cfg.fadeIn(int ticks)
cfg.fadeOut(int ticks)
cfg.fade(int fadeInTicks, int fadeOutTicks)
```

Configures linear fade transitions. See [The fade system](#the-fade-system) below.

### Uniforms

```java
cfg.uniform(String blockName, float value)
cfg.uniform(String blockName, float x, float y)
cfg.uniform(String blockName, float x, float y, float z)
cfg.uniform(String blockName, float x, float y, float z, float w)
cfg.uniform(String blockName, int value)
cfg.uniform(String blockName, DoubleSupplier supplier)
cfg.uniform(String blockName, IntSupplier supplier)
cfg.uniformVec2(String blockName, Supplier<Vector2fc> supplier)
cfg.uniformVec3(String blockName, Supplier<Vector3fc> supplier)
cfg.uniformVec4(String blockName, Supplier<Vector4fc> supplier)
cfg.uniformMat4(String blockName, Supplier<Matrix4fc> supplier)
cfg.uniformRaw(String blockName, Supplier<List<UniformValue>> supplier)
```

`blockName` is the GLSL uniform block name, not an individual member name. See [Uniforms](Uniforms) for a full explanation and the complete supplier reference.

### Textures

```java
cfg.texture(String samplerName, Identifier textureId)
```

Overrides the texture bound to a named sampler for this effect. `samplerName` matches the `sampler_name` value in the JSON pipeline (without the "Sampler" suffix that GLSL uses).

### Callbacks

```java
cfg.onBeforeApply(Consumer<PostEffectContext> callback)
cfg.onAfterApply(Consumer<PostEffectContext> callback)
```

Called each frame immediately before and after the effect is rendered. See [PostEffectContext](#posteffectcontext) below.

---

## PostEffectHandle

`PostEffectHandle` is returned by every `PostEffects.register` call. It provides runtime control over the effect.

### Enable / disable

```java
handle.enable()
handle.disable()
handle.setEnabled(boolean enabled)
handle.isEnabled()
```

A disabled effect is never rendered regardless of its condition supplier.

### Condition

```java
handle.setCondition(BooleanSupplier condition)
```

Replaces the condition supplier. Pass `() -> true` to make the effect unconditional.

### Phase and priority

```java
handle.setPhase(RenderPhase phase)
handle.setPriority(int priority)
```

### External targets

```java
handle.setExternalTargets(Set<Identifier> targets)
```

### Fade

```java
handle.setFadeIn(int ticks)
handle.setFadeOut(int ticks)
handle.setFade(int fadeInTicks, int fadeOutTicks)
```

### Uniforms

The same uniform overload set available on `PostEffectConfig` is available on the handle:

```java
handle.uniform(String blockName, float value)
handle.uniform(String blockName, float x, float y)
handle.uniform(String blockName, float x, float y, float z)
handle.uniform(String blockName, float x, float y, float z, float w)
handle.uniform(String blockName, int value)
handle.uniform(String blockName, DoubleSupplier supplier)
handle.uniform(String blockName, IntSupplier supplier)
handle.uniformVec2(String blockName, Supplier<Vector2fc> supplier)
handle.uniformVec3(String blockName, Supplier<Vector3fc> supplier)
handle.uniformVec4(String blockName, Supplier<Vector4fc> supplier)
handle.uniformMat4(String blockName, Supplier<Matrix4fc> supplier)
handle.uniformRaw(String blockName, Supplier<List<UniformValue>> supplier)
```

### Textures

```java
handle.texture(String samplerName, Identifier textureId)
```

### Callbacks

```java
handle.onBeforeApply(Consumer<PostEffectContext> callback)
handle.onAfterApply(Consumer<PostEffectContext> callback)
```

### Status

```java
handle.isActive()
handle.isEnabled()
handle.getId()
```

### Unregistration

```java
handle.unregister()
```

Permanently removes the effect from the registry. The handle should not be used after this call.

---

## PostEffectContext

`PostEffectContext` is passed to `onBeforeApply` and `onAfterApply` callbacks each frame.

```java
ctx.getClient()
ctx.getDeltaTick()
ctx.getScreenWidth()
ctx.getScreenHeight()
ctx.getProcessor()
```

---

## RenderPhase

```java
RenderPhase.POST_WORLD
RenderPhase.POST_RENDER
```

`POST_WORLD` injects the effect after the world has rendered but before Minecraft clears the main depth buffer for hand + overlay rendering. This means `minecraft:main` depth sampling works in this phase. The effect covers the world, but anything rendered after the depth clear (hand and screen overlays) will appear on top of the post effect.

`POST_RENDER` injects at the end of `GameRenderer.render()`, after everything including the GUI. The effect covers the entire final frame.

Amnetic preserves depth for this phase in two steps. It snapshots world depth before vanilla clears the main depth buffer for hand rendering, and it also snapshots the later hand-inclusive depth right before vanilla clears depth again for GUI rendering. Right before `POST_RENDER` effects run, Amnetic restores that later hand-inclusive snapshot into `minecraft:main`, so default depth sampling in `POST_RENDER` includes first-person hand/item depth.

Amnetic also exposes that preserved depth snapshot as an external framebuffer target with id `amnetic:world_depth_snapshot`. In a post-effect JSON you can bind it like this:

```json
{
  "sampler_name": "WorldDepth",
  "target": "amnetic:world_depth_snapshot",
  "use_depth_buffer": true
}
```

This is the most reliable way to read preserved world-only depth in `POST_RENDER`, because it does not depend on sampling `minecraft:main` depth after vanilla has already cleared it earlier in the frame.

---

## Priority ordering

When multiple effects are registered for the same phase, they are applied in descending priority order: the effect with the highest priority number is applied first to the framebuffer, and the effect with the lowest priority is applied last (closest to display).

Default priority is `0`. Use negative values if you need an effect to run after the defaults.

---

## The fade system

When `fadeIn` or `fadeOut` is configured on an effect, Amnetic tracks a per-effect `intensity` float ranging from `0.0` to `1.0`.

- When the condition transitions from false to true, `intensity` ramps linearly from `0.0` to `1.0` over `fadeIn` ticks.
- When the condition transitions from true to false, `intensity` ramps linearly from `1.0` to `0.0` over `fadeOut` ticks.
- If either value is `0`, the transition is instant in that direction.

Each frame, the current `intensity` is injected as the `Intensity` uniform block:

```glsl
layout(std140) uniform Intensity {
    float Value;
};
```

Your shader must declare this block and multiply the visual effect strength by `Intensity.Value` to honor the fade. The block name `"Intensity"` is reserved by Amnetic and should not be used for any other purpose in effects that use fade.

If no fade is configured, the `Intensity` block is not injected and you do not need to declare it in your shader.
