# Decals

Decals are textures projected onto existing world geometry: bullet holes, scorch marks, graffiti, blood splatter, moss. You don't build any geometry for them. You place a box in the world, and whatever surfaces fall inside that box get the texture stamped onto them, terrain, entities, and Amnetic models alike.

```java
import com.meekdev.amnetic.client.decal.Decals;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// a 2x2 block scorch mark on the floor at (100, 64, 200)
Decal scorch = Decals.box(
        Identifier.fromNamespaceAndPath("mymod", "textures/decal/scorch.png"),
        new Vec3(100, 64.01, 200), // box center
        new Vector3f(0, 1, 0), // facing: sticks to upward-facing surfaces
        2f, 2f, 0.5f); // width, height, depth (projection thickness)

scorch.opacity(0.9f).tint(0.2f, 0.2f, 0.2f);

// later
scorch.remove();
```

That's the whole lifecycle. A decal is live the moment `Decals.box(...)` returns; there is no `start()` and nothing to register. Amnetic drives the decal pass itself every frame, so you never call `Decals.render()` yourself unless you're replacing the default pipeline. Call `remove()` to drop a decal permanently, or `Decals.clear()` to drop them all.

---

## How the decal pass works

Decals are screen-space projected. The renderer never draws a quad in the world. Instead, each decal is one fullscreen pass that:

1. Copies the main framebuffer's depth into a capture target.
2. Reconstructs every pixel's world position from that depth and the inverse view-projection matrix.
3. Transforms the position into the decal's local space (the box maps to `[-0.5, 0.5]` on each axis) and discards any pixel outside the box.
4. Projects the texture along the box's facing axis: the local X/Z coordinates become the texture UVs.
5. Estimates the surface normal at the pixel from depth neighbors and fades the decal where the surface doesn't face the decal's axis.
6. Alpha-blends the result onto the main framebuffer.

Because it works from the depth buffer, a decal wraps over whatever is actually there: steps, slabs, entity silhouettes, curved model surfaces. Sky pixels (depth at the far plane) are always skipped.

### Projection box semantics

The box is defined by a center (`Vec3`, world blocks), a facing `normal`, and three sizes:

- **`width`** spans the box's local right axis.
- **`height`** spans the box's local up axis.
- **`depth`** spans the facing normal, i.e. how far the projection reaches into and out of the surface.

The normal is the surface orientation the decal sticks to: `(0, 1, 0)` for a floor decal, `(0, 0, -1)` for a north-facing wall, and so on. The in-plane right axis is derived as `normal x (0, 1, 0)` (falling back to `(1, 0, 0)` when the normal is vertical), and up completes the basis. All sizes are full extents, so a `width` of 2 covers 2 blocks.

Keep `depth` modest. It's the tolerance for how far a surface can sit off the box's midplane and still receive the decal; a huge depth on a floor decal will also stamp the ceiling if the box reaches it. The decal fades out smoothly over the outer 30 percent of the depth range, so surfaces near the front or back face of the box get a soft edge rather than a hard clip.

### Angle fade

`angleFade` controls how much a surface may deviate from the decal's facing before the decal disappears. The shader computes the dot product between the reconstructed surface normal and the decal normal, then fades with `smoothstep(angleFade, 1.0, dot)`. At the default `0.3`, surfaces facing the decal get full strength and the decal dies off on steep or perpendicular geometry, which prevents the classic smearing artifact where a floor decal stretches down the side of a step. Lower values are more permissive (more wrap, more stretch); `1.0` restricts the decal to surfaces almost exactly aligned with the normal.

---

## Creating and tuning decals

`Decals.box` is the only factory. Every setter on the returned `Decal` chains, and everything is live: change a field and the next frame renders it.

```java
Decal d = Decals.box(tex, center, new Vector3f(0, 1, 0), 2f, 2f, 0.5f)
        .opacity(0.8f) // overall alpha multiplier, clamped 0..1. default 1
        .angleFade(0.4f) // surface-alignment cutoff, clamped 0..1. default 0.3
        .tint(0.3f, 0.3f, 0.3f);// multiplied into the texture RGB. default white

// move / reshape it later
d.center(new Vec3(101, 64.01, 200))
 .normal(0, 1, 0)
 .width(3f).height(3f).depth(0.4f);
```

`tint` multiplies the texture color, so white shows the texture as-is and darker tints work well for scorch and grime. Sizes clamp to `>= 0`; a zero-length normal is ignored rather than applied.

`remove()` flags the decal and drops it from the active list permanently. There is no disable toggle; if you want a decal to blink, remove and respawn it, or drive `opacity` to 0 (an opacity-0 decal still runs its fullscreen pass, so prefer `remove()` for anything long-lived).

---

## Textures

The texture is any resource `Identifier` pointing at a PNG, e.g. `minecraft:textures/block/mud.png` or `mymod:textures/decal/crack.png`. The renderer registers and loads it on first use; you don't preload anything. Textures imported through the editor's texture importer work too.

Sampling is nearest-filtered and clamped to edge, so decal textures stay crisp and never tile or bleed at the border. The texture's alpha channel is the decal's shape: transparent texels leave the surface untouched.

If the texture can't be resolved (or you passed `null`), the plain path falls back to a procedural soft disc in the tint color, a radial fade that reads as a generic scorch or blob. Handy for prototyping before art exists.

---

## The two shader paths

Every decal renders its color through `decal.fsh`, the plain path described above: texture times tint, alpha-blended onto the main target. Since decals draw before the deferred lighting pass, that color becomes part of the albedo the light pass reads, so plain decals are lit like the surface they sit on. For most stains and marks this is all you need.

A decal can additionally be **relightable**: as soon as you give it a normal map or a roughness override, it also writes the [G-buffer](Render-Pipeline), and deferred lighting treats the decaled pixels as their own material.

```java
Decals.box(albedo, pos, new Vector3f(0, 1, 0), 2f, 2f, 0.3f)
     .normalMap(Identifier.fromNamespaceAndPath("mymod", "textures/decal/crack_n.png"))
     .roughness(0.4f)
     .metallic(0.1f);
```

Relightable decals run a second pass through `decal_gbuffer.fsh`, after the color pass. It writes into the gbuffer's normal and material attachments only (never albedo, never depth), using the same box test and angle fade as the color pass, with `opacity * fade` as the blend weight. The normal map is sampled in the decal's tangent space (right/up/normal basis) with its X/Y components boosted 3x for punchier relief; without a normal map the decal writes its flat facing normal. The material write carries roughness and metallic with material id 0 (default PBR).

The rules of the relightable path:

- `writesGBuffer()` becomes true when `normalMap` is set or `roughness >= 0`. Roughness uses `-1` as the "don't override" sentinel; set `roughness(-1f)` and clear the normal map to turn the gbuffer write back off.
- If a decal is relightable through its normal map alone, the material write uses a roughness fallback of `0.7`.
- The pass requires the gbuffer to be populated this frame (the normal-fill pass ran). If deferred lighting isn't active, the gbuffer pass silently skips and you keep the plain color result.
- `metallic` clamps to `0..1` and only matters on relightable decals; the plain path ignores it.

---

## Ordering and blending

Decals run in the `GEOMETRY` stage of Amnetic's [render pipeline](Render-Pipeline) at order 50, after the gbuffer normal fill, instanced meshes, and models, and before the shadow and deferred lighting passes. That placement is what makes decals participate in lighting: their color lands in the frame before the light pass captures it, and their gbuffer writes land before lighting reads normals and materials.

Because the whole `GEOMETRY` stage runs under vanilla translucents, water and glass correctly draw over decals.

Within the pass, decals draw in creation order with standard alpha blending (`SRC_ALPHA, ONE_MINUS_SRC_ALPHA`, destination alpha preserved). Overlapping decals stack painter-style: the most recently created decal composites on top. There is no z-ordering or priority field; if you need a specific stack, create the decals in that order (the editor's Duplicate button, for instance, puts the copy on top).

---

## Where it sits in the frame

```
GEOMETRY   gbuffer fill -> instanced meshes -> models -> decals   <- here
LIGHTING   shadow map -> deferred lighting
SCREEN_SPACE, AFTER_WATER, ATMOSPHERE, POST ...
```

The pass early-outs when there are no decals, so an empty list costs nothing. Depth is copied from the main target once per frame that decals render, and each decal is one fullscreen draw (two for relightable decals), so cost scales with decal count times screen resolution regardless of how small the decal looks on screen.

---

## Editor

You can place and tune decals live. Open the in-game editor and navigate to Renderer then Decals. You get spawn controls (texture picker, size, and an optional relightable block with normal map, roughness, and metallic), and a live list of every active decal with position, normal, size, opacity, angle fade, tint, and the relight material, plus Duplicate and Remove.

The viewport draws a wireframe gizmo box around every decal showing its exact projection volume, with a center dot you can click to select the decal (the selected one highlights and its inspector entry opens). Toggle the gizmos with the "Show decal gizmos" checkbox in the panel.

---

## Limitations

* Every decal is a fullscreen pass, even a tiny one in the distance. Cost is per decal times screen pixels; a few dozen are fine, hundreds are not. There is no frustum culling or screen-rect scissoring.
* Decals only appear on geometry that wrote depth into the main target before the `GEOMETRY` stage: terrain, entities, and vanilla-rendered content. Amnetic instanced meshes and models drawn in the same stage are covered too, but anything drawn after the decal pass (translucents, `AFTER_WATER` geometry) is not.
* Surface normals come from depth reconstruction, so the angle fade uses geometric normals only; it doesn't know about normal-mapped surface detail underneath.
* No rotation control in the decal plane. The texture's orientation is fixed by the derived right/up basis; for a floor decal, rotating the artwork means rotating the texture itself.
* No z-order or priority between overlapping decals beyond creation order, and no per-decal enable toggle. Remove and recreate.
* Relightable decals need the deferred pipeline's gbuffer populated that frame; without it they degrade to the plain color path.

---

## Reference

### `Decals`

```java
static Decal box(Identifier texture, Vec3 center, Vector3f normal,
                 float width, float height, float depth); // create + register, live immediately
static List<Decal> active(); // the live list (iteration-safe)
static boolean hasRelightable(); // any active decal writing the gbuffer?
static void clear(); // remove every decal
static void render(); // driven by Amnetic's pipeline; don't call yourself
static void dispose(); // frees GPU resources; called on client shutdown
```

### `Decal` (all setters chain, all live)

```java
Decal center(Vec3 c); // box center, world blocks
Decal normal(float x, float y, float z); // facing / projection axis. normalized; zero ignored
Decal width(float v); // full extent on the right axis, >= 0
Decal height(float v); // full extent on the up axis, >= 0
Decal depth(float v); // projection thickness along the normal, >= 0
Decal opacity(float v); // alpha multiplier, clamped 0..1. default 1
Decal angleFade(float v); // alignment cutoff, clamped 0..1. default 0.3
Decal tint(float r, float g, float b); // multiplied into texture RGB. default 1,1,1

// relightable (gbuffer) path
Decal normalMap(Identifier id); // tangent-space normal map; null clears it
Decal roughness(float v); // material override; < 0 = off (the default, -1)
Decal metallic(float v); // clamped 0..1. default 0. relightable path only

boolean writesGBuffer(); // normalMap set or roughness >= 0
float roughnessOr(float fallback); // roughness, or fallback when not overridden

void remove(); // permanent
boolean isRemoved();

// read-only accessors
Identifier texture(); Vec3 center(); Vector3f normal();
float width(); float height(); float depth();
float opacity(); float angleFade(); Vector3f tint();
Identifier normalMap(); float metallic();
```

---

## See Also

- [Deferred Lights](Deferred-Lights). The lighting pass that lights plain decals as albedo and relights gbuffer decals as their own material.
- [Render Pipeline](Render-Pipeline). Where the decal pass slots in, and how to reorder or replace it.
- [Framebuffers](Framebuffers). The depth capture and reconstruction machinery the pass is built on.
