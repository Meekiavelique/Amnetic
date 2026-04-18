# Writing Shaders

This page covers the pipeline JSON format, GLSL conventions for Minecraft 1.21.11 post-processing shaders, and a complete worked example.

---

## File locations

| File | Path |
|---|---|
| Pipeline descriptor | `assets/[namespace]/post_effect/[name].json` |
| Fragment shader | `assets/[namespace]/shaders/post/[name].fsh` |

The `[name]` in the pipeline path must match the path component of the `Identifier` you pass to `PostEffects.register`. For example, `Identifier.of("mymod", "frost")` loads `assets/mymod/post_effect/frost.json`.

---

## Pipeline JSON format

```json
{
    "targets": {
        "swap": {}
    },
    "passes": [
        {
            "vertex_shader": "minecraft:core/screenquad",
            "fragment_shader": "mymod:post/my_effect",
            "inputs": [
                { "sampler_name": "In", "target": "minecraft:main" }
            ],
            "output": "swap",
            "uniforms": {
                "MyConfig": [
                    { "name": "Strength", "type": "float", "value": 0.0 },
                    { "name": "Color",    "type": "vec4",  "value": [1.0, 0.0, 0.0, 1.0] }
                ]
            }
        },
        {
            "vertex_shader": "minecraft:core/screenquad",
            "fragment_shader": "minecraft:post/blit",
            "inputs": [{ "sampler_name": "In", "target": "swap" }],
            "output": "minecraft:main",
            "uniforms": {
                "BlitConfig": [
                    { "name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0] }
                ]
            }
        }
    ]
}
```

### targets

Declares intermediate framebuffers that exist only within this pipeline. `"swap"` is a conventional name for the intermediate buffer used in a two-pass setup. You can declare multiple targets for multi-pass effects.

Targets declared here are local to this effect. External targets (such as `minecraft:main`) are referenced by their full namespaced identifier in `inputs` and `output`.

### passes

An ordered list of render passes. Each pass runs the declared fragment shader over the full screen quad.

| Field | Description |
|---|---|
| `vertex_shader` | Use `"minecraft:core/screenquad"` for all fullscreen post effects. You do not need a custom vertex shader. |
| `fragment_shader` | Namespaced path to your fragment shader, without the `.fsh` extension. The file lives at `assets/[namespace]/shaders/[path].fsh`. |
| `inputs` | List of samplers bound for this pass. Each entry provides a `sampler_name` and a `target` that identifies the framebuffer to read from. |
| `output` | The framebuffer to write to. Use a local target name (e.g. `"swap"`) or `"minecraft:main"` for the final output. |
| `uniforms` | Map of block name to list of uniform member descriptors. Values here are the defaults used when Amnetic does not override them. |

### Uniform member descriptor fields

| Field | Description |
|---|---|
| `name` | The GLSL member variable name inside the block |
| `type` | One of `"float"`, `"int"`, `"vec2"`, `"vec3"`, `"vec4"`, `"mat4"` |
| `value` | Default value. Use a number for scalars, an array for vector and matrix types |

---

## GLSL conventions

### Version

Always declare `#version 330` at the top of your fragment shader.

### Vertex inputs

The `minecraft:core/screenquad` vertex shader provides one interpolated varying:

```glsl
in vec2 texCoord;
```

You do not need to declare any other vertex inputs.

### Sampler naming

The GLSL sampler name is the JSON `sampler_name` value with `"Sampler"` appended:

| JSON `sampler_name` | GLSL uniform name |
|---|---|
| `"In"` | `InSampler` |
| `"Depth"` | `DepthSampler` |
| `"Noise"` | `NoiseSampler` |

```glsl
uniform sampler2D InSampler;
```

### SamplerInfo block

Every pass automatically receives the `SamplerInfo` UBO from the engine. Always declare it:

```glsl
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};
```

`OutSize` and `InSize` are populated by the engine. You do not need to supply them.

### Uniform blocks

Each entry in the JSON `"uniforms"` map corresponds to a `layout(std140)` block in GLSL. The JSON key is the block name:

```glsl
layout(std140) uniform MyConfig {
    float Strength;
    vec4  Color;
};
```

Uniform block members must be declared in the same order as in the JSON descriptor.

### Output

```glsl
out vec4 fragColor;
```

Write your final color to `fragColor`. Alpha is generally kept at `1.0` unless the output target has transparency compositing requirements.

---

## The Intensity block (fade)

When `fadeIn` or `fadeOut` is configured on an effect, Amnetic injects the `Intensity` uniform block each frame:

```glsl
layout(std140) uniform Intensity {
    float Value;
};
```

You must declare this block in your shader if your effect uses fade. Multiply the strength of your visual effect by `Intensity.Value`:

```glsl
vec4 result = mix(scene, applyEffect(scene), Intensity.Value);
fragColor = result;
```

If you do not declare the block when fade is configured, the uniform will be uploaded but not used, which is harmless but wasteful.

---

## The two-pass pattern

Most post effects follow a two-pass setup:

1. **Effect pass** - reads from `minecraft:main`, applies the effect, writes to a local `"swap"` target.
2. **Blit pass** - reads from `"swap"`, copies back to `minecraft:main` using the engine's `minecraft:post/blit` shader.

This pattern is necessary because you cannot read and write the same framebuffer in a single pass. All the example JSON on this page uses this pattern.


## Multi-pass effects

You can add as many passes as needed. Each pass reads from its declared inputs and writes to its declared output. Passes execute in order. Use additional local targets to chain passes:

```json
{
    "targets": {
        "blur_h": {},
        "blur_v": {}
    },
    "passes": [
        { "fragment_shader": "mymod:post/blur_h", "inputs": [{"sampler_name": "In", "target": "minecraft:main"}], "output": "blur_h", ... },
        { "fragment_shader": "mymod:post/blur_v", "inputs": [{"sampler_name": "In", "target": "blur_h"}],          "output": "blur_v", ... },
        { "fragment_shader": "minecraft:post/blit", "inputs": [{"sampler_name": "In", "target": "blur_v"}],         "output": "minecraft:main", ... }
    ]
}
```
