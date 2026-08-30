# Shading Models

A `ShadingModel` changes how a model's pixels are shaded. It is built from a **lighting base** and optional **GLSL snippets**, and you assign one per `ModelMaterial`, so different materials on the same model can shade differently.

There are two bases. `ShadingModel.pbr()` is the normal Cook-Torrance path in the deferred lighting pass. `ShadingModel.flat()` shades like a vanilla Minecraft block: the texel multiplied by the lightmap and by the per-face brightness the game applies to each of the six directions, with no specular, no image-based lighting, and no tonemap.

Two modifiers compose on top of either base. `fragment(...)` replaces the shading with your own snippet, and `vertex(...)` displaces the geometry.

```java
import com.meekdev.amnetic.client.material.ShadingModel;
import net.minecraft.resources.Identifier;

// snippets live in your resources as .glsl files
Identifier sway  = Identifier.fromNamespaceAndPath("mymod", "shaders/material/sway.glsl");
Identifier sheen = Identifier.fromNamespaceAndPath("mymod", "shaders/material/sheen.glsl");
Identifier snippet = sheen;

ShadingModel plain    = ShadingModel.pbr();                       // Cook-Torrance
ShadingModel blocky   = ShadingModel.flat();                      // block-authored look

ShadingModel swaying  = ShadingModel.pbr().vertex(sway);          // lit normally, geometry moves
ShadingModel grass    = ShadingModel.flat().vertex(sway);         // block-authored art that sways
ShadingModel sheened  = ShadingModel.flat().fragment(sheen);      // stylised over the flat result
ShadingModel custom   = ShadingModel.pbr().fragment(snippet);     // custom BRDF
ShadingModel both     = ShadingModel.pbr().fragment(sheen).vertex(sway);
```

A fragment snippet is the body of a function. It receives a `GBufferSample s` and must `return` a `vec3` colour.

```java
import com.meekdev.amnetic.client.material.ShadingModel;
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelMaterial;
import com.meekdev.amnetic.client.model.Models;

// Cheap cel-shader: quantize the already-computed PBR radiance into bands,
// then re-tint with albedo.
ShadingModel celShaded = ShadingModel.pbr().fragment(
        "float lum = dot(s.radiance, vec3(0.299, 0.587, 0.114));\n" +
        "float bands = 3.0;\n" +
        "float quant = floor(lum * bands + 0.5) / bands;\n" +
        "return s.albedo * quant;");

Model model = Models.load(Identifier.fromNamespaceAndPath("mymod", "models3d/robot.glb"));
for (ModelMaterial m : model.materials()) {
    m.setShadingModel(celShaded);
}
```

`fragment` also takes an `Identifier` pointing at a `.glsl` file in your resources, which is the better choice for anything longer than a few lines. The file is read through the normal shader loader, so it can use `#include`.

The full worked example lives at `examples/src/main/java/com/example/material/ShadingDemo.java`.

## How dispatch works

Building a shading model hands its snippet to an internal registry, which assigns it an integer ID. ID 0 is reserved for the default PBR path and never has a snippet.

The registry generates a virtual GLSL include, `amnetic:shaders/material/custom_ladder.glsl`. There is no file on disk. The include resolves to generated source containing the `GBufferSample` struct definition and a `shadeCustomMaterial` function that is an `if (materialId == N) { ... }` ladder over every registered snippet. The deferred lighting fragment shader (`shaders/light/deferred.fsh`) pulls it in with a normal `#include` directive.

IDs whose snippet resolves to the same GLSL text share a single branch, emitted as `if (materialId == 3 || materialId == 7) { ... }`. This is what lets a parameterised effect register many variants that differ only in their uniform data without paying for a copy of the code per variant.

Registering a snippet marks the registry dirty. The deferred lighting pass checks this flag once per frame and invalidates its shader program when something changed, so the next frame recompiles the uber-shader with the new ladder spliced in. Registration is therefore safe at any time, including mid-game, at the cost of a one-time shader relink.

At the end of the deferred pass, after the standard lighting result has been computed, the shader runs:

```glsl
if (materialId != 0) {
    bool handled;
    vec3 custom = shadeCustomMaterial(materialId,
            GBufferSample(albedo, N, fragPos, rough, metallic, outColor, lightmap), handled);
    if (handled) outColor = custom;
}
```

Your snippet's returned colour replaces the standard result for that pixel. If the material ID matches no registered snippet, or your snippet's branch falls through without returning, `handled` comes back false and the standard result is kept.

## The GBufferSample struct

The generated struct passed to a fragment snippet:

| Field | Type | Meaning |
|---|---|---|
| `albedo` | `vec3` | The scene colour at this pixel as already drawn by the model shader (tonemapped, sRGB). On a `flat()` base this holds the flat result |
| `normal` | `vec3` | World-space surface normal from the G-buffer |
| `fragPos` | `vec3` | Camera-relative position reconstructed from depth (the camera sits at the origin) |
| `roughness` | `float` | Roughness from the G-buffer material target, with specular AA widening already applied (widened by the pixel-footprint normal variance) |
| `metallic` | `float` | Metallic from the G-buffer material target |
| `radiance` | `vec3` | The fully composed standard lighting result: sun shadowing, local light radiance, and specular. Use it to tint or quantize instead of re-lighting from scratch. Unused on a `flat()` base |
| `lightmap` | `vec3` | The vanilla lightmap colour sampled at this fragment |

Note that `albedo` is not the raw material base colour. The deferred pass reads the main colour buffer, which already contains the model shader's lit, tonemapped output. For band-and-tint styles this is usually what you want anyway; sample `radiance` when you want the version that also includes deferred light contributions.

## Vertex displacement

`vertex(...)` attaches a snippet to the model's **vertex** stage instead of the deferred pass. It receives a `VertexSample v` and must `return` a world-space offset that gets added to the vertex.

| Field | Type | Meaning |
|---|---|---|
| `localPos` | `vec3` | Model space, after skinning |
| `worldPos` | `vec3` | World space, camera-relative when `WorldSpace` is set |
| `origin` | `vec3` | The instance's own translation |
| `uv` | `vec2` | Texture coordinate |
| `time` | `float` | Seconds, for animating the displacement |

The normal and tangent are rebuilt from the displaced surface so lighting follows the deformation. Doing that costs three evaluations of your snippet per vertex, so keep it cheap.

Vertex snippets go into a second generated include, `amnetic:shaders/material/custom_vertex_ladder.glsl`, spliced into the model vertex shader. It carries its own dirty flag, because the model pass and the deferred pass relink independently.

## ID bands

The ID rides in an 8-bit G-buffer channel, so the whole space is 0-255 and it is split into three bands:

| Range | Meaning |
|---|---|
| `0` | Default PBR, no snippet |
| `1` - `127` | `pbr()` based models |
| `128` - `254` | `flat()` based models. The model shader shades these like a block, and the snippet still runs in the deferred pass with `s.albedo` holding that flat result |
| `255` | Plain `flat()` with no snippet |

The split exists because a flat base has to be handled in the *model* shader rather than the deferred pass: it replaces the lighting rather than tinting the result of it, and by the time the deferred pass runs the fragment has already been through Cook-Torrance with no way back to the raw texel. `model.fsh` branches on `MaterialId >= FLAT_SHADED_BASE` to decide, which is why the boundary is a fixed constant in both Java and GLSL.

## How the material ID travels

`ModelMaterial.setShadingModel(model)` stores the integer ID on the material. When the model draws, the model shader receives it as the `MaterialId` uniform and writes it into the G-buffer material target: `GMaterial = vec4(roughness, metallic, materialId / 255.0, lightLevels)`.

The deferred pass samples that target and decodes it back:

```glsl
uniform sampler2D GMaterialSampler; // r = roughness, g = metallic, b = materialId/255, a = light levels
...
materialId = int(gm.z * 255.0 + 0.5);
```

The vanilla-geometry fill pass writes material ID 0, so custom shading only ever applies to pixels covered by Amnetic model geometry. Plain decals do not touch the G-buffer at all; a G-buffer-writing decal writes material ID 0 blended by its coverage, so an opaque one resets the ID under it.

## Per-material parameters

A snippet that needs numbers can read them from a shader storage buffer indexed by the same material ID the ladder branched on, bound at binding 1 in the deferred pass:

```glsl
layout(std430, binding = 1) readonly buffer MaterialParamData { vec4 materialParams[]; };

vec4 lane0 = materialParams[materialId * 3 + 0];
```

Three `vec4` lanes are reserved per ID. Writing them from Java is `MaterialParams.INSTANCE.set(id, floats...)`, which takes up to twelve floats and uploads on the next frame. Combined with the ladder's snippet dedupe, this is how one snippet serves many parameterised variants: every variant gets its own ID for its own parameters, but they all share one copy of the code. [Subsurface Scattering](Subsurface-Scattering) is built exactly this way.

## The G-buffer's role

The `GBuffer` (see `com.meekdev.amnetic.client.gbuffer.GBuffer`) is a set of extra render targets, normal, material, emissive, and depth, that Amnetic model geometry fills while drawing. Deferred lighting is a fullscreen pass that runs later with no access to the original geometry; everything it knows about a pixel comes from these targets. The shading model system exists entirely inside that hand-off: the material ID is just one more per-pixel value the geometry pass records and the lighting pass reads back. If the G-buffer is disabled (`GBuffer.disable()`) or unpopulated, the deferred pass falls back to depth-reconstructed normals with material ID 0, and custom shading never fires.

## Limitations

* Custom shading runs inside the deferred lighting pass. That pass early-outs when zero deferred lights are registered (the gate is the light registry being empty, not visibility), so with no registered `Light` your snippet does not run and the material shows its standard forward result.
* 127 PBR-based and 126 flat-based shading models, imposed by the 8-bit G-buffer channel and the band split.
* IDs are assigned in registration order at runtime and are never persisted: models loaded from the `.ammesh` cache always come back with the default PBR model, so reassign custom shading models after loading.
* Snippets are not validated before splicing. One bad snippet takes down the whole deferred lighting shader, which disables deferred lighting until fixed.
* Custom shading applies only to Amnetic model geometry. Vanilla terrain and entities always carry material ID 0, and G-buffer-writing decals blend the ID back toward 0.
* Snippets replace the final colour for the pixel. There is no way to feed a custom result back into later stages such as SSGI or bloom beyond what the colour itself carries.
* A vertex snippet is evaluated three times per vertex so the normal can be rebuilt.

## See Also

* [Models](Models) for loading models and mutating materials
* [Subsurface Scattering](Subsurface-Scattering) for a worked parameterised shading model
* [Deferred Lights](Deferred-Lights) for the pass your snippet runs in
* [Writing Shaders](Writing-Shaders) for general GLSL conventions
