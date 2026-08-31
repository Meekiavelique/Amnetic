# Emissive

Emissive surfaces are the ones that glow on their own: a lamp, a neon sign, the hot part of a furnace. Amnetic collects them into a dedicated buffer that bloom reads, so a glowing surface blooms because it *is* emissive, not because it happens to be bright.

Three kinds of geometry can contribute, and they reach the buffer by different routes.

| Producer | How it emits |
|---|---|
| Amnetic models | The model shader writes `GEmissive` into the G-buffer |
| Instanced meshes and particles | `InstancedMesh.Builder.emissive(strength)`, re-drawn into the emissive buffer |
| Blocks | [`BlockEmissive`](#blocks), a built-in emissive source |
| Anything else (entities, GeckoLib, your own renderer) | [`EmissiveSources`](#the-hook), the generic hook |

All four are additive: the emissive buffer is cleared, the re-render sources draw into it, and the G-buffer's emissive target is added on top.

## Blocks

Off by default. One call turns it on:

```java
import com.meekdev.amnetic.client.emissive.BlockEmissive;

BlockEmissive.enable();
BlockEmissive.chunkRadius(4);    // how far to gather, in chunks
BlockEmissive.intensity(1.5f);   // multiplier on the glow
```

| Method | Meaning |
|---|---|
| `enable()` / `disable()` / `isEnabled()` | Registers or removes the built-in block source |
| `chunkRadius(int)` | Gather radius in chunks, default `4`, minimum `1` |
| `intensity(float)` | Multiplier applied to every emissive block, default `1.0` |
| `invalidate()` | Force a rebuild on the next frame |

Every block whose state reports `getLightEmission() > 0` contributes. Its quads are pulled from the baked block model, cached per `BlockState`, and drawn sampling the block atlas.

### Why it is off by default

Gathering costs CPU. It is cheap in the common case — see below — but it is not free, and the cost scales with `chunkRadius`. Turning it on is a deliberate choice.

### How the scan stays cheap

The naive approach, walking every block position in a radius, is far too expensive: a 32-block cube is ~275,000 block lookups per rebuild.

Instead the gather works on chunk sections and rejects most of them without ever touching a block. `LevelChunkSection.maybeHas(...)` tests the section's **palette**, so a section that contains no light-emitting block anywhere is discarded in a single call. Only sections that pass get their 4096 positions walked.

At the default radius that is 1,944 sections, nearly all of which fail the palette test immediately.

Rebuilds are triggered when:

* the camera crosses a **section** boundary (not every few blocks)
* a block change alters `getLightEmission()` — placing stone does not trigger one
* `invalidate()` is called

Between rebuilds the mesh is reused, and when a rebuild fits in the existing buffer it uploads with `glBufferSubData` instead of reallocating.

## Per-texel masks

A whole glowing block is often wrong. A lantern's metal frame should stay dark while its panel glows. Amnetic narrows the glow in two stages.

**Per-quad emission.** `BakedQuad.MaterialInfo` carries a `lightEmission()` value, so a model can already mark individual faces as emitting. When a quad reports non-zero emission that value is used; otherwise the block's own light level applies. This costs nothing and needs no extra textures.

**Per-texel mask.** For each quad, Amnetic looks for a sprite named after its texture with an `_e` suffix — `minecraft:block/lantern` looks for `minecraft:block/lantern_e`. If that sprite is in the block atlas, the quad's atlas UV is converted into sprite-local space and remapped into the mask sprite's range, so the fragment shader samples the mask directly. The mask's brightness (max of RGB, times alpha) scales the glow, and texels below `0.004` are discarded.

The mask sprite is a normal texture: black where the surface should not glow, bright where it should.

> **The mask only applies if the `_e` sprite is stitched into the block atlas.** Minecraft stitches a texture only when a model references it or an `atlases/blocks.json` sprite source lists it. A bare `_e` file in a resource pack is *not* enough on its own.
>
> When the sprite is absent the lookup detects it — the atlas returns its missing-texture sprite, and Amnetic compares the returned sprite's name against the one requested — and the quad falls back to uniform per-quad emission. It never renders the missing-texture checkerboard as a mask.

## The hook

Anything that can draw itself can contribute, without Amnetic knowing what it is. This is the route for entities, GeckoLib models, block entities, or a renderer of your own.

```java
import com.meekdev.amnetic.client.emissive.EmissiveSources;

EmissiveSources.register(myId, ctx -> {
    // the emissive framebuffer is already bound; draw your glowing geometry
});

EmissiveSources.unregister(myId);
```

| Method | Meaning |
|---|---|
| `register(Identifier, Consumer<EmissiveContext>)` | Add a source, replacing any with the same id |
| `unregister(Identifier)` | Remove it |
| `isEmpty()` | Whether any source is registered |

`EmissiveContext` carries what a draw needs:

| Method | Meaning |
|---|---|
| `level()` | The client level |
| `cameraPos()` | Camera position in world space |
| `view()`, `projection()` | The current matrices |
| `deltaTick()` | Partial tick |
| `targetWidth()`, `targetHeight()` | Size of the bound emissive target |

Draw whatever should glow, in whatever colour it should glow. The buffer is HDR, so values above 1.0 are meaningful and bloom more strongly.

A source that throws is logged and skipped for that frame; it never takes down the bloom pass. `BlockEmissive` is itself implemented as one of these sources, so the block path exercises the same code you would.

## Bloom

Bloom is the consumer. It gathers from all sources into one buffer, then runs its pyramid.

Note that when the G-buffer is populated the bloom prefilter reads emissive **only** from the G-buffer's emissive target, so ordinary bright-pixel bloom driven by `BloomSettings.threshold()` does not apply to those pixels. Emissive is what glows.

## Limitations

* Block emissive is off by default and its cost scales with `chunkRadius`.
* The gather caps at 400,000 vertices. A build dense enough to exceed that will silently stop adding geometry.
* `_e` masks require the sprite to be stitched into the block atlas; a loose file in a resource pack does nothing.
* Block glow is unlit and unshaded — it is the texel colour times the mask times the strength, not a lighting calculation.
* The emissive buffer is screen-space. A glowing surface hidden behind geometry contributes nothing.
* Sources registered through the hook are drawn in map iteration order, with no depth sorting between them.

## See Also

* [Bloom](Bloom) for the pass that consumes the emissive buffer
* [Shading Models](Shading-Models) for how models write `GEmissive`
* [Instanced Rendering](Instanced-Rendering) for `InstancedMesh.Builder.emissive`
* [Deferred Lights](Deferred-Lights) if you want a glowing surface to actually light its surroundings
