# Instanced Rendering

Instancing draws one shape many times in a single draw call. Normally each object you draw is its own draw call, and a few hundred of those will tank your frame rate. With instancing you upload one small record per object, like its position and color, and the GPU stamps the shape out thousands of times at once. Use it for anything repeated: markers, projectiles, leaves, debris, spell motes, debug shapes, sprite swarms.

---

## How it works

An instanced mesh is four things:

1. **Geometry**. The shape, drawn once per object. A quad, a cube, a circle, or your own vertices. It is a `MeshData` and gets uploaded once.

2. **Per-instance data**. The small record that changes per object. Usually a transform matrix, or a transform and a color.

3. **A shader**. Turns the geometry and the per-instance data into pixels. Amnetic ships built-in shaders for the common cases, so often you write no GLSL.

4. **A render callback**. Runs every frame. You add the objects that should be visible this frame.

You describe all of that once with a builder and call `register`. After that, every frame Amnetic calls your callback, you fill a batch, and it draws the whole batch in one call. The batch is cleared for you each frame, so you just add whatever is visible right now. There is no add or remove bookkeeping.

Positions are camera-relative. The context gives you `worldToModel(...)` helpers that subtract the camera position for you. This is what the shaders expect and it keeps precision good far from the world origin.

---

## Example 1. A box on every mob

This draws a translucent red cube on every mob. `BuiltinShader.TRANSFORM_COLOR` already bundles the layout, the writer and the GLSL, so all you give is the geometry and the per-frame objects.

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

That is the whole feature. `register` is what makes it draw. A mesh you `build` but never register does nothing.

### The three built-in shaders

- `BuiltinShader.TRANSFORM`. Record `Transform(Matrix4fc transform)`. Solid white shapes, like debug volumes.
- `BuiltinShader.TRANSFORM_COLOR`. Record `TransformColor(Matrix4fc transform, Vector4fc color)`. Colored shapes. The common case.
- `BuiltinShader.TEXTURED_BILLBOARD`. Record `TexturedBillboard(Vector3fc center, float size, Vector4fc color, Vector4fc uv)`. Textured sprites that face the camera. There is a short constructor `new TexturedBillboard(center, size, color)` that uses the whole texture. Pair it with `.texture(...)` and `.geometry(MeshData.texturedQuad())`.

---

## Example 2. A custom shader

When the built-ins are not enough, you give your own layout, writer and shader.

First, describe the per-instance data with a layout. Each call adds an attribute at a vertex-attribute location. A `mat4` takes four locations in a row. The geometry owns location 0 (position), and location 1 too if it is textured (UV), so your instance attributes start after that.

```java
// per instance: a model matrix at locations 1..4, then an RGBA color at location 5
InstanceLayout layout = InstanceLayout.builder()
    .mat4(1)
    .vec4(5)
    .build();
```

Then write a packer that puts one object into the buffer, in the same order as the layout.

```java
record Mote(Matrix4fc transform, Vector4fc color) {}

InstanceWriter<Mote> writer = (mote, p) ->
    p.putMat4(mote.transform()).putVec4(mote.color());
```

Then build the mesh with your shader id. `.shader(Identifier.of("mymod", "instance/mote"))` loads `assets/mymod/shaders/instance/mote.vsh` and `.fsh`.

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

The vertex shader. Geometry comes in at location 0, and the instance matrix and color at the locations your layout used. Amnetic uploads `ProjViewMatrix`, `ProjectionMatrix` and `ViewMatrix`, so declare the ones you use.

```glsl
#version 330 core
layout(location = 0) in vec3 Position;       // from MeshData

layout(location = 1) in vec4 InstTransform0; // the mat4, one column at a time
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

The fragment shader.

```glsl
#version 330 core
in vec4 vColor;
out vec4 FragColor;
void main() {
    if (vColor.a < 0.001) discard;
    FragColor = vColor;
}
```

The most common bug with custom shaders is mismatched locations. The geometry uses location 0, and location 1 if the mesh is textured. Your instance attributes must start at the next free location, and a `mat4` takes four. `TRANSFORM` and `TRANSFORM_COLOR` start their instance data at location 1. `TEXTURED_BILLBOARD` starts at location 2, because a textured mesh already uses location 1 for the UV. The packer must put fields in the same order the layout declares them, or the instances come out as garbage.

If you set `.texture(...)`, it is bound to texture unit 0 and your shader reads it with `uniform sampler2D TextureSampler;`.

---

## Reference

### Builder

```java
// pick one builder:
InstancedMesh.builder(BuiltinShader<T> shader)                          // built-in: layout, writer and GLSL included
InstancedMesh.builder(InstanceLayout layout, InstanceWriter<T> writer)  // custom shader

.geometry(MeshData geometry)      // required: the shape drawn per instance
.shader(Identifier id)            // required for the custom builder; assets/<ns>/shaders/<path>.vsh and .fsh
.texture(Identifier id)           // optional: bound to TextureSampler, unit 0
.phase(InstancePhase phase)       // default WORLD_LAST
.renderState(RenderState state)   // default RenderState.DEFAULT
.onRender((ctx, batch) -> { ... })// required: fill the batch each frame
.build()                          // returns InstancedMesh<T>
.register(Identifier id)          // build AND register; this is what makes it draw
```

`geometry`, `onRender` and a shader are required. `register` is what makes it render. `build` on its own does nothing.

### MeshData (geometry)

```java
MeshData.quad()                  // flat 1x1 quad, position only
MeshData.texturedQuad()          // flat quad with UVs, for billboards
MeshData.unitCircle(int segs)    // disc of radius 1, segs >= 3
MeshData.unitCube()              // 1x1x1 cube at the origin
MeshData.of(float[] verts, int[] indices)
MeshData.of(float[] verts)
```

Location 0 is always `vec3 Position`. Location 1 is `vec2 UV`, only for textured geometry. Triangles only. Indexed meshes draw with `glDrawElementsInstanced`, the rest with `glDrawArraysInstanced`.

### InstanceLayout (custom per-instance format)

```java
InstanceLayout.builder()
    .mat4(int startLocation)   // four vec4 columns, startLocation to +3
    .vec4(int location)
    .vec3(int location)
    .vec2(int location)
    .float1(int location)
    .build();
```

The presets the built-ins use: `TRANSFORM` is `mat4(1)`, `TRANSFORM_COLOR` is `mat4(1).vec4(5)`, `TEXTURED_BILLBOARD` is `vec3(2).float1(3).vec4(4).vec4(5)`. Every instance attribute advances once per instance, not per vertex.

### InstancePacker (used inside a custom writer)

```java
p.putFloat(float)
 .putVec2(x, y)
 .putVec3(x, y, z) / .putVec3(Vector3fc)
 .putVec4(x, y, z, w) / .putVec4(Vector4fc)
 .putMat4(Matrix4fc);
```

Write the fields in the same order as the layout.

### RenderState

```java
RenderState.DEFAULT       // depth test on, depth write on, no blend, cull on
RenderState.TRANSLUCENT   // depth test on, depth write off, alpha blend, cull on
RenderState.ADDITIVE      // depth test on, depth write off, additive blend, cull off

RenderState.builder()
    .depthTest(boolean).depthWrite(boolean)
    .blend(BlendMode.NONE | ALPHA | ADDITIVE | MULTIPLY)
    .backfaceCulling(boolean).build();
```

The state is applied before the draw and reset afterward. Use `TRANSLUCENT` for tinted overlays and `ADDITIVE` for glows.

### InstanceRenderContext

Passed to `onRender` each frame. It has the frame state and the camera-relative matrix helpers. Always place instances with these, not with raw world coordinates.

```java
Minecraft client();  ClientLevel world();  float deltaTick();
Vec3 cameraPos();  Matrix4fc viewMatrix();  Matrix4fc projectionMatrix();

Matrix4f worldToModel(Vec3 pos);
Matrix4f worldToModel(double x, double y, double z);
Matrix4f worldToModel(BlockPos pos);                         // centered on the block
Matrix4f worldToModel(Vec3 pos, float scale);
Matrix4f worldToModel(Vec3 pos, float yawDegrees, float scale);
Matrix4f worldToModel(Vec3 pos, Quaternionfc rotation, float scale);
Matrix4f worldToModel(Entity entity, ...);                   // interpolated by deltaTick
```

---

## Status

Rendering is driven from the client initializer. On Fabric's `LevelRenderEvents.END_MAIN`, Amnetic binds Minecraft's main framebuffer and calls `InstanceMeshRegistry.renderAll` for the `WORLD_LAST` phase. In 26.1 there is no bindable GL framebuffer, so Amnetic builds its own from the main target's color and depth textures (`MainTargetFramebuffer`).

Only `WORLD_LAST` is driven right now. Other phases exist in the enum but nothing calls `renderAll` for them, so a mesh registered with those will not draw. Use `WORLD_LAST`, which is the default. If meshes register without error but nothing shows up, this wiring and the framebuffer bind are the first place to check after a Minecraft update.

---

## See Also

- [Particles](Particles). CPU-driven billboards built on this pipeline.
- [Camera](Camera). Effects and a cinematic director.
- [Mesh Pipeline](Mesh-Pipeline). Single, non-instanced world geometry.
- [Writing Shaders](Writing-Shaders). GLSL conventions.
