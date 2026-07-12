# Shading Models

A `ShadingModel` changes how the deferred lighting pass shades a model's pixels. The built-in `ShadingModel.DEFAULT_PBR` uses the normal Cook-Torrance path. Custom models register a GLSL snippet that gets baked into the deferred lighting uber-shader as a dispatch case keyed on a small integer material ID. You assign a shading model per `ModelMaterial`, so different materials on the same model can shade differently.

The snippet is the body of a function. It receives a `GBufferSample s` and must `return` a `vec3` color.

```java
import com.meekdev.amnetic.client.material.ShadingModel;
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelMaterial;
import com.meekdev.amnetic.client.model.Models;

// Cheap cel-shader: quantize the already-computed PBR radiance into bands,
// then re-tint with albedo.
ShadingModel celShaded = ShadingModel.custom(
        "float lum = dot(s.radiance, vec3(0.299, 0.587, 0.114));\n" +
        "float bands = 3.0;\n" +
        "float quant = floor(lum * bands + 0.5) / bands;\n" +
        "return s.albedo * quant;");

Model model = Models.load(Identifier.fromNamespaceAndPath("mymod", "models3d/robot.glb"));
for (ModelMaterial m : model.materials()) {
    m.setShadingModel(celShaded);
}
```

The full worked example lives at `examples/src/main/java/com/example/material/ShadingDemo.java`.

## How dispatch works

`ShadingModel.custom(snippet)` hands the snippet to an internal registry, which assigns it the next free integer ID starting at 1. ID 0 is reserved for the default PBR path and never has a snippet.

The registry generates a virtual GLSL include, `amnetic:shaders/material/custom_ladder.glsl`. There is no file on disk. The include resolves to generated source containing the `GBufferSample` struct definition and a `shadeCustomMaterial` function that is an `if (materialId == N) { ... }` ladder over every registered snippet. The deferred lighting fragment shader (`shaders/light/deferred.fsh`) pulls it in with a normal `#include` directive.

Registering a snippet marks the registry dirty. The deferred lighting pass checks this flag once per frame and invalidates its shader program when something changed, so the next frame recompiles the uber-shader with the new ladder spliced in. Registration is therefore safe at any time, including mid-game, at the cost of a one-time shader relink.

At the end of the deferred pass, after the standard lighting result has been computed, the shader runs:

```glsl
if (materialId != 0) {
    bool handled;
    vec3 custom = shadeCustomMaterial(materialId,
            GBufferSample(albedo, N, fragPos, rough, metallic, outColor), handled);
    if (handled) outColor = custom;
}
```

Your snippet's returned color replaces the standard result for that pixel. If the material ID matches no registered snippet, or your snippet's branch falls through without returning, `handled` comes back false and the standard result is kept.

## The GBufferSample struct

The generated struct passed to your snippet:

| Field | Type | Meaning |
|---|---|---|
| `albedo` | `vec3` | The scene color at this pixel as already drawn by the model shader (tonemapped, sRGB) |
| `normal` | `vec3` | World-space surface normal from the G-buffer |
| `fragPos` | `vec3` | Camera-relative position reconstructed from depth (the camera sits at the origin) |
| `roughness` | `float` | Roughness from the G-buffer material target, with specular AA widening already applied (widened by the pixel-footprint normal variance) |
| `metallic` | `float` | Metallic from the G-buffer material target |
| `radiance` | `vec3` | The fully composed standard lighting result: sun shadowing, local light radiance, and specular. Use it to tint or quantize instead of re-lighting from scratch |

Note that `albedo` is not the raw material base color. The deferred pass reads the main color buffer, which already contains the model shader's lit, tonemapped output. For band-and-tint styles this is usually what you want anyway; sample `radiance` when you want the version that also includes deferred light contributions.

## Snippet rules

The snippet is spliced verbatim inside an `if` block, so it can contain multiple statements and declare locals. It must end by returning a `vec3`. The GLSL version is 430 core and the snippet can call anything already defined earlier in `deferred.fsh` and its includes, though only the `GBufferSample` contract is stable.

There is no validation on the Java side. A syntax error in any snippet breaks compilation of the whole deferred lighting shader, which disables deferred lighting until fixed.

## How the material ID travels

`ModelMaterial.setShadingModel(model)` stores the integer ID on the material. When the model draws, the model shader receives it as the `MaterialId` uniform and writes it into the G-buffer material target: `GMaterial = vec4(roughness, metallic, materialId / 255.0, 0.0)`.

The deferred pass samples that target and decodes it back:

```glsl
uniform sampler2D GMaterialSampler; // r = roughness, g = metallic, b = materialId/255, a = unused
...
materialId = int(gm.z * 255.0 + 0.5);
```

The ID rides in an 8-bit channel, so 255 custom shading models is the hard ceiling. The vanilla-geometry fill pass writes material ID 0, so custom shading only ever applies to pixels covered by Amnetic model geometry. Plain decals do not touch the G-buffer at all; a G-buffer-writing decal writes material ID 0 blended by its coverage, so an opaque one resets the ID under it.

## The G-buffer's role

The `GBuffer` (see `com.meekdev.amnetic.client.gbuffer.GBuffer`) is a set of extra render targets, normal, material, emissive, and depth, that Amnetic model geometry fills while drawing. Deferred lighting is a fullscreen pass that runs later with no access to the original geometry; everything it knows about a pixel comes from these targets. The shading model system exists entirely inside that hand-off: the material ID is just one more per-pixel value the geometry pass records and the lighting pass reads back. If the G-buffer is disabled (`GBuffer.disable()`) or unpopulated, the deferred pass falls back to depth-reconstructed normals with material ID 0, and custom shading never fires.

## Limitations

* Custom shading runs inside the deferred lighting pass. That pass early-outs when zero deferred lights are registered (the gate is the light registry being empty, not visibility), so with no registered `Light` your snippet does not run and the material shows its standard forward result.
* Maximum of 255 custom shading models, imposed by the 8-bit G-buffer channel.
* IDs are assigned in registration order at runtime and are never persisted: models loaded from the `.ammesh` cache always come back with the default PBR model, so reassign custom shading models after loading.
* Snippets are not validated before splicing. One bad snippet takes down the whole deferred lighting shader.
* Custom shading applies only to Amnetic model geometry. Vanilla terrain and entities always carry material ID 0, and G-buffer-writing decals blend the ID back toward 0.
* Snippets replace the final color for the pixel. There is no way to feed a custom result back into later stages such as SSGI or bloom beyond what the color itself carries.

## See Also

* [Models](Models) for loading models and mutating materials
* [Deferred Lights](Deferred-Lights) for the pass your snippet runs in
* [Writing Shaders](Writing-Shaders) for general GLSL conventions
