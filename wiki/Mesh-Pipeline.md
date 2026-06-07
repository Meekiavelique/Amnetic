# Mesh Pipeline

The Mesh Pipeline lets you draw your own geometry in the world using your own shaders. This is different from post-processing, which operates on the finished 2D frame, and from particles, which draw camera-facing sprites. The Mesh Pipeline draws 3D triangles positioned in the world. You can use it for things like force-field domes, fog walls, energy beams, ground decals, or selection boxes.

It is intentionally simple. You give it a vertex shader, a fragment shader, and up to two textures. It gives you back a Minecraft `RenderType`. You use that `RenderType` to emit vertices the Minecraft way. Everything visual is controlled by your shaders.

If you need to draw many copies of the same mesh, like thousands of markers or projectiles, use Instanced Rendering instead, which handles all copies in a single draw call. The Mesh Pipeline is the right tool for one-off geometry you build each frame.

---

## How it works

When you call `MeshPipeline.renderType(...)` it returns a cached `RenderType` configured as follows:

- The vertex format is `POSITION_TEX_COLOR_NORMAL`, drawn as quads. Each vertex carries a position, one UV, an RGBA color, and a normal.
- Positions are in camera-relative world space. You subtract the camera position before emitting vertices. This keeps floating-point precision high when you are far from the world origin.
- Three samplers are available: `Sampler0` and `Sampler1` are your two textures, and `DepthSampler` is the scene depth. You can use scene depth to produce soft edges where your mesh meets solid geometry.
- Blending is enabled, back faces are culled, depth is tested but not written. Overlapping surfaces will not fight the depth buffer.

The pipeline caches one `RenderType` per shader-and-texture combination and rebuilds automatically on resource reload. Calling `renderType(...)` every frame is cheap.

---

## Example: a dome

### 1. Get the render type

You can call this every frame and the result is cached internally.

```java
RenderType domeType = MeshPipeline.renderType(
    Identifier.of("core", "worldmesh"),
    Identifier.of("mymod", "post/forcefield"),
    Identifier.of("mymod", "textures/fx/hex.png"),
    Identifier.of("mymod", "textures/fx/noise.png"));
```

### 2. Emit geometry during a world render event

Positions must be in camera-relative coordinates.

```java
Vec3 cam = client.gameRenderer.getMainCamera().position();
VertexConsumer vc = bufferSource.getBuffer(domeType);
PoseStack.Pose pose = poseStack.last();

for (Quad q : domeQuads) {
    for (Vertex v : q.vertices()) {
        vc.addVertex(pose,
                (float)(v.x - cam.x),
                (float)(v.y - cam.y),
                (float)(v.z - cam.z))
            .setUv(v.u, v.v)
            .setColor(0.4f, 0.8f, 1.0f, 0.5f)
            .setNormal(pose, v.nx, v.ny, v.nz);
    }
}

bufferSource.endBatch();
```

### 3. Write the fragment shader

The built-in `worldmesh` vertex shader provides the varyings shown below. Your fragment shader reads from them directly.

```glsl
#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D DepthSampler;

in vec3 vPos;
in vec2 vUV;
in vec4 vColor;
in vec3 vNormal;

out vec4 fragColor;

void main() {
    float hex = texture(Sampler0, vUV).r;
    vec3 rgb = vColor.rgb * hex;
    float rim = pow(1.0 - abs(dot(normalize(vNormal), normalize(-vPos))), 2.0);
    fragColor = vec4(rgb + rim, vColor.a * (hex + rim));
}
```

---

## The `worldmesh` vertex shader contract

`worldmesh` is a built-in vertex shader you can use without writing your own. It outputs four varyings:

```glsl
in vec3 vPos;   // camera-relative position
in vec2 vUV;
in vec4 vColor;
in vec3 vNormal;
```

Because the camera sits at the origin in this space, the view direction toward any fragment is simply `normalize(-vPos)`. This makes fresnel and rim effects straightforward. If you need the world-space position, add the camera position back.

`worldmesh` also clamps geometry to within the far plane, so large or distant meshes remain visible even when the player's render distance is low.

---

## Reference

```java
static RenderType MeshPipeline.renderType(
    Identifier vertexShader,
    Identifier fragmentShader,
    Identifier texture0,
    Identifier texture1);
```

- Shaders resolve as `assets/<ns>/shaders/<path>.vsh` / `.fsh`.
- Two texture identifiers are always required, even if your shader only samples one.
- The returned `RenderType` is cached per `(vertexShader, fragmentShader, texture0, texture1)` tuple, so repeated calls are cheap.

---

## See also

- [Instanced Rendering](Instanced-Rendering) - many repeated meshes in a single draw call
- [Particles](Particles) - camera-facing billboards and the shared scene-depth texture
- [Vanilla Depth and Fog](Vanilla-Depth-and-Fog) - depth sampling and linearization, for soft edges