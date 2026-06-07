# Instanced Rendering

Instancing draws **one mesh many times in a single draw call**. Instead of issuing a draw per object — which collapses your frame rate at a few hundred objects — you upload a small record per object (its transform, its color, …) into one buffer and the GPU stamps the mesh out thousands of times in one go. Use it for anything repeated: position markers, projectiles, foliage, falling leaves, debris, spell motes, debug shapes, sprite swarms.

This page explains the model, walks through complete examples (built-in shader *and* custom shader), and gives the per-type reference. Read the **Status** section at the bottom first if you're trying to get it running today.

---

## The mental model

An instanced mesh is four things bolted together:

1. **Geometry** (`MeshData`) — the shape drawn once per instance (a quad, a cube, a circle, or your own vertices). Uploaded to the GPU once.
2. **Per-instance data** — the small record that differs per object. For the common cases this is "a transform matrix" or "a transform + a color".
3. **A shader** — turns geometry + per-instance data into pixels. Amnetic ships **built-in shaders** for the three usual cases, so you often write no GLSL at all.
4. **A render callback** (`onRender`) — runs every frame; you add the instances that should be visible *this* frame.

You describe all of that once with a builder and `register(...)` it. From then on, every frame Amnetic calls your callback, you fill a batch, and it draws the whole batch in one instanced call. The batch is cleared for you before each call, so you simply re-add whatever is currently visible — no manual add/remove bookkeeping.

Positions are handled in **camera-relative space**: the context gives you `worldToModel(...)` helpers that subtract the camera position, which both matches what the instance shaders expect and keeps precision high far from the origin.

---

## Worked example 1 — built-in shader (no GLSL)

Draw a translucent red cube on every mob in the world. `BuiltinShader.TRANSFORM_COLOR` bundles the layout, the writer, and the GLSL, so all you provide is geometry and the per-frame instances.

```java
import com.meekdev.amnetic.client.instanced.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import org.joml.Matrix4f;
import org.joml.Vector4f;

InstancedMesh.builder(BuiltinShader.TRANSFORM_COLOR)
    .geometry(MeshData.unitCube())
    .renderState(RenderState.TRANSLUCENT)
    .onRender((ctx, batch) -> {
        for (var entity : ctx.world().entitiesForRendering()) {
            if (!(entity instanceof Mob)) continue;
            Matrix4f model = ctx.worldToModel(entity, 0.4f);   // camera-relative, scaled to 0.4
            batch.add(new BuiltinShader.TransformColor(model, new Vector4f(1f, 0.2f, 0.2f, 0.6f)));
        }
    })
    .register(Identifier.of("mymod", "mob_markers"));
```

That's the whole feature. `register(...)` is what makes it draw — a mesh you `build()` but never register does nothing. Your callback runs once per frame; add exactly the instances you want visible and Amnetic draws them all in one call.

### The three built-in shaders

| Built-in | Instance record `T` | Use it for |
|---|---|---|
| `BuiltinShader.TRANSFORM` | `Transform(Matrix4fc transform)` | solid white shapes (debug volumes, masks) |
| `BuiltinShader.TRANSFORM_COLOR` | `TransformColor(Matrix4fc transform, Vector4fc color)` | colored/tinted shapes — the common case |
| `BuiltinShader.TEXTURED_BILLBOARD` | `TexturedBillboard(Vector3fc center, float size, Vector4fc color, Vector4fc uv)` | textured screen-facing sprites |

`TexturedBillboard` has a short constructor that uses the whole texture: `new TexturedBillboard(center, size, color)`. Pair it with `.texture(...)` and `.geometry(MeshData.texturedQuad())`.

---

## Worked example 2 — custom shader

When the built-ins aren't enough (you need extra per-instance data, or custom vertex/fragment math), supply your own layout, writer, and shader.

**1. Describe the per-instance data with a layout.** Each call adds an attribute at a vertex-attribute *location*; `mat4` takes four consecutive locations. Geometry owns location 0 (position) and, for textured geometry, location 1 (UV) — so your instance attributes start after that:

```java
// per instance: a model matrix (locations 1..4) + an RGBA color (location 5)
InstanceLayout layout = InstanceLayout.builder()
    .mat4(1)
    .vec4(5)
    .build();
```

**2. Write a packer** that serializes one instance into the buffer, in the same field order as the layout:

```java
record Mote(Matrix4fc transform, Vector4fc color) {}

InstanceWriter<Mote> writer = (mote, p) ->
    p.putMat4(mote.transform()).putVec4(mote.color());
```

**3. Build the mesh with your shader id.** `.shader(Identifier.of("mymod","instance/mote"))` loads `assets/mymod/shaders/instance/mote.vsh` and `.fsh`:

```java
InstancedMesh.builder(layout, writer)
    .geometry(MeshData.unitCircle(16))
    .shader(Identifier.of("mymod", "instance/mote"))
    .renderState(RenderState.ADDITIVE)
    .onRender((ctx, batch) -> {
        for (var m : myMotes) {
            batch.add(new Mote(ctx.worldToModel(m.pos(), m.scale()), m.color()));
        }
    })
    .register(Identifier.of("mymod", "motes"));
```

**4. The shaders.** Vertex — `assets/mymod/shaders/instance/mote.vsh`. Geometry comes in at location 0; the instance matrix/color at the locations your layout used. Amnetic uploads `ProjViewMatrix`, `ProjectionMatrix`, and `ViewMatrix` (declare the ones you use):

```glsl
#version 330 core
layout(location = 0) in vec3 Position;     // from MeshData

layout(location = 1) in vec4 InstTransform0; // the mat4, column by column
layout(location = 2) in vec4 InstTransform1;
layout(location = 3) in vec4 InstTransform2;
layout(location = 4) in vec4 InstTransform3;
layout(location = 5) in vec4 InstColor;

uniform mat4 ProjViewMatrix;
out vec4 vColor;

void main() {
    mat4 model = mat4(InstTransform0, InstTransform1, InstTransform2, InstTransform3);
    gl_Position = ProjViewMatrix * model * vec4(Position, 1.0);
    vColor = InstColor;
}
```

Fragment — `assets/mymod/shaders/instance/mote.fsh`:

```glsl
#version 330 core
in vec4 vColor;
out vec4 FragColor;
void main() {
    if (vColor.a < 0.001) discard;
    FragColor = vColor;
}
```

> **The number-one custom-shader bug** is mismatched locations. The geometry uses location 0 (and location 1 if the mesh is textured). Your instance attributes must start at the next free location, and a `mat4` consumes four. `TRANSFORM`/`TRANSFORM_COLOR` start their instance data at location 1 (plain mesh, only location 0 used); `TEXTURED_BILLBOARD` starts at location 2 (textured mesh uses location 1 for UV). The packer's `put...` order must match the layout's attribute order exactly, or instances render garbage.

If you set `.texture(...)`, it's bound to texture unit 0 and your shader samples it via `uniform sampler2D TextureSampler;`.

---

## Reference

### InstancedMesh / builder

```java
// pick ONE builder:
InstancedMesh.builder(BuiltinShader<T> shader)             // built-in: layout + writer + GLSL included
InstancedMesh.builder(InstanceLayout layout, InstanceWriter<T> writer)  // custom shader

// configure (fluent):
.geometry(MeshData geometry)      // REQUIRED — the mesh drawn per instance
.shader(Identifier id)            // REQUIRED for the custom builder; selects assets/<ns>/shaders/<path>.{vsh,fsh}
.texture(Identifier id)           // optional — bound to TextureSampler / unit 0
.phase(InstancePhase phase)       // default WORLD_LAST (see Status note)
.renderState(RenderState state)   // default RenderState.DEFAULT
.onRender((ctx, batch) -> { ... })// REQUIRED — fill the batch each frame

// finish:
.build()                          // -> InstancedMesh<T>
.register(Identifier id)          // build() AND register it to actually draw
```

`geometry`, `onRender`, and a shader are mandatory. **`register` is what makes it render** — `build()` alone doesn't.

### MeshData — geometry

```java
MeshData.quad()                  // flat XZ quad, 1×1 (position only)
MeshData.texturedQuad()          // flat XY quad with UVs — for billboards (position + UV)
MeshData.unitCircle(int segs)    // XZ disc, radius 1, segs ≥ 3 (position only)
MeshData.unitCube()              // 1×1×1 cube centered at origin (position only)
MeshData.of(float[] verts, int[] indices)   // custom indexed mesh
MeshData.of(float[] verts)                   // custom non-indexed mesh
```

Geometry attribute locations are fixed: **location 0 = `vec3 Position`**, **location 1 = `vec2 UV`** (only for textured geometry like `texturedQuad`). Indexed meshes draw via `glDrawElementsInstanced`, non-indexed via `glDrawArraysInstanced`. Triangles only.

### InstanceLayout — custom per-instance format

```java
InstanceLayout.builder()
    .mat4(int startLocation)   // four vec4 columns at startLocation..+3
    .vec4(int location)
    .vec3(int location)
    .vec2(int location)
    .float1(int location)
    .build();
```

Presets (used by the built-ins): `TRANSFORM` = `mat4(1)`; `TRANSFORM_COLOR` = `mat4(1).vec4(5)`; `TEXTURED_BILLBOARD` = `vec3(2).float1(3).vec4(4).vec4(5)`. All instance attributes are set with a divisor of 1 — they advance once per instance, not per vertex.

### InstancePacker — used inside a custom writer

```java
p.putFloat(float)
 .putVec2(x, y)
 .putVec3(x, y, z)  / .putVec3(Vector3fc)
 .putVec4(x, y, z, w) / .putVec4(Vector4fc)
 .putMat4(Matrix4fc);
```

Write fields in the same order as the layout declares them.

### RenderState

```java
RenderState.DEFAULT       // depth test on, depth write on, no blend, cull on
RenderState.TRANSLUCENT   // depth test on, depth write OFF, alpha blend, cull on
RenderState.ADDITIVE      // depth test on, depth write OFF, additive blend, cull OFF

RenderState.builder()
    .depthTest(boolean).depthWrite(boolean)
    .blend(BlendMode.NONE | ALPHA | ADDITIVE | MULTIPLY)
    .backfaceCulling(boolean).build();
```

State is applied before the draw and reset to sane defaults afterward. Use `TRANSLUCENT` for tinted overlays, `ADDITIVE` for glows.

### InstanceRenderContext

Passed to `onRender` each frame. Frame state plus camera-relative matrix helpers — **always position instances with these**, never with raw world coordinates:

```java
Minecraft client();  ClientLevel world();  float deltaTick();
Vec3 cameraPos();  Matrix4fc viewMatrix();  Matrix4fc projectionMatrix();

Matrix4f worldToModel(Vec3 pos);
Matrix4f worldToModel(double x, double y, double z);
Matrix4f worldToModel(BlockPos pos);                          // centered on the block
Matrix4f worldToModel(Vec3 pos, float scale);
Matrix4f worldToModel(Vec3 pos, float yawDegrees, float scale);
Matrix4f worldToModel(Vec3 pos, Quaternionfc rotation, float scale);
Matrix4f worldToModel(Entity entity[, float scale][, float yawDegrees]);  // interpolated by deltaTick
```

---

## Status — how this is wired today

The instancing API above is the intended, complete usage. **Before relying on it, be aware of how it is currently hooked into the renderer**, because the runtime wiring is the part most likely to need attention after a Minecraft update:

- **Rendering is driven from a `GameRenderer` mixin** (`WorldLastInstancingMixin`) that, after the world renders, binds the main framebuffer and calls `InstanceMeshRegistry.renderAll(WORLD_LAST, ...)`. For instancing to draw, that mixin must be (a) compiled — it and the `client/instanced/**` package are gated by a `sourceSets` exclude in `build.gradle` — and (b) listed in the `client` array of `amnetic.mixins.json`.
- **Only `InstancePhase.WORLD_LAST` is actually driven.** `BEFORE_ENTITIES` and `AFTER_ENTITIES` exist in the enum but no hook calls `renderAll` for them yet — a mesh registered with those phases will not render. Use `WORLD_LAST` (the default).
- The matrix-supplying path used by the Fabric-event overload of `renderAll` depends on a `GameRenderer` invoker accessor; the active mixin path passes the view/projection matrices directly and does not need it.

If instanced meshes register without error but nothing appears on screen, this wiring is the first place to check. (If you've just ported to a new Minecraft version, re-check the `renderLevel` `@Redirect` signature in the mixin against the current `LevelRenderer.renderLevel` parameters.)

---

## See also

- [Particles](Particles) — CPU-driven camera-facing billboards; simpler than `TEXTURED_BILLBOARD` instancing for sprite swarms
- [Mesh Pipeline](Mesh-Pipeline) — single, non-instanced world geometry through Minecraft's own render types
- [Writing Shaders](Writing-Shaders) — GLSL conventions
