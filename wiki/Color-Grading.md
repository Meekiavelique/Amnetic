# Color Grading

Color grading is the final look pass: it takes the lit, composited frame and pushes it toward a mood. Amnetic's grade is a single fullscreen pass with the usual photographic controls (exposure, contrast, saturation, brightness, white balance, gamma) plus optional LUT support for baked looks authored in an external tool.

The pass is already registered in Amnetic's [render pipeline](Render-Pipeline); it's just **off by default**. Turning it on is one call, and every control lives on one settings object:

```java
import com.meekdev.amnetic.client.grade.ColorGrade;

ColorGrade.settings()
    .enabled(true)
    .exposure(1.1f)
    .contrast(1.05f)
    .saturation(0.9f)
    .temperature(0.15f); // warm it up
```

`ColorGrade.enable()` and `ColorGrade.disable()` are shorthand for `settings().enabled(true/false)`.

---

## How it works

The pass copies the main target's color into a capture buffer, then draws a fullscreen triangle back onto the main target through `shaders/grade/colorgrade.fsh`. The operations run in a fixed order per pixel:

1. **Exposure** multiplies the color.
2. **Temperature** shifts red up and blue down (or the reverse); **tint** shifts green.
3. **Brightness** is added flat.
4. **Contrast** pivots the color around mid-gray (`0.5`).
5. **Saturation** blends between the Rec. 709 luminance and the full color.
6. **Gamma** applies a `pow(c, 1/gamma)` curve.
7. The result is clamped to `[0, 1]`.
8. If a **LUT** is set, the clamped color is remapped through it and blended in by `lutIntensity`.

Because of the final clamp, everything after the grade (only the [CAS sharpen](TAA) in the default pipeline) sees an LDR image. HDR effects like bloom run before the grade for exactly this reason.

All settings are plain uniforms uploaded every frame, so every knob is free to animate; nothing rebuilds buffers when you change a value.

---

## The knobs

Everything lives on `ColorGrade.settings()`, and every setter chains.

| Setting       | Default | Clamp        | Meaning                                                              |
|---------------|---------|--------------|----------------------------------------------------------------------|
| `enabled`     | `false` | -           | Master switch. While off, the pass is a no-op.                        |
| `exposure`    | `1.0`   | `>= 0`       | Linear multiplier on the scene color. Applied first.                  |
| `contrast`    | `1.0`   | `>= 0`       | Pivot around mid-gray. `1` is neutral, `0` flattens to gray.          |
| `saturation`  | `1.0`   | `>= 0`       | `0` is grayscale, `1` neutral, above `1` oversaturates.               |
| `brightness`  | `0.0`   | unclamped    | Flat additive offset, applied before contrast.                        |
| `temperature` | `0.0`   | `[-1, 1]`    | Warm/cool white balance. Positive is warmer (more red, less blue).    |
| `tint`        | `0.0`   | `[-1, 1]`    | Green/magenta balance. Positive pushes green.                         |
| `gamma`       | `1.0`   | `>= 0.01`    | Output gamma curve. Above `1` lifts midtones, below `1` crushes them. |
| `lut`         | `null`  | -           | Identifier of a LUT strip texture, or `null` for no LUT.              |
| `lutSize`     | `16`    | `>= 2`       | Grid size of the LUT (a 16-LUT is a 256x16 texture).                  |
| `lutIntensity`| `1.0`   | `[0, 1]`     | Blend between the graded color and the LUT result.                    |

Temperature and tint are simple channel offsets scaled by `0.1`, so even the full `[-1, 1]` range is a moderate shift, not a color inversion.

---

## LUTs

For looks that are hard to dial in with sliders, you can bake the whole grade into a lookup table and let the shader remap colors through it:

```java
import net.minecraft.resources.Identifier;

ColorGrade.settings()
    .enabled(true)
    .lut(Identifier.fromNamespaceAndPath("mymod", "textures/lut/teal_orange.png"))
    .lutSize(16)
    .lutIntensity(0.8f);
```

The expected format is the common **horizontal strip**: for a size-`N` LUT the texture is `N*N` pixels wide and `N` tall, laid out as `N` slices side by side. Red maps to the horizontal axis within a slice, green to the vertical axis, and blue selects the slice; the shader manually interpolates between the two nearest slices, so banding on blue gradients is smoothed. The default `lutSize` of `16` matches the classic 256x16 neutral strip you can screenshot, grade in any image editor, and save back out.

The LUT texture is loaded lazily through the vanilla texture manager the first time the pass runs with it set, so a plain PNG in your resource pack works. Textures imported at runtime through Amnetic's texture import path are picked up directly without a resource-pack file. If the texture can't be resolved, the pass simply runs without the LUT rather than failing.

Note that the LUT is applied **after** the slider operations and the LDR clamp, on the already-graded color. `lutIntensity` lets you use a strong LUT at partial strength instead of authoring a weaker one.

---

## Where it sits in the frame

The pass is registered at `POST 20` in the default pipeline (see [Render Pipeline](Render-Pipeline)):

```
POST  5   TAA           resolve
POST 10   Bloom
POST 20   Color Grade
POST 30   CAS Sharpen
```

So the grade sees the temporally resolved frame with bloom already composited, and its LDR output is what the sharpen pass crisps. Like every Amnetic screen pass it skips itself under Iris, and it's wrapped in a failure guard: a bad frame logs to `Amnetic/ColorGrade` and repeated failures back the pass off instead of taking world rendering down (see [Shader Hot Reload](Shader-Hot-Reload) for how a fixed shader revives it).

---

## Editor

The live controls are in the editor under **Renderer > Post FX**, in the **Color Grade** collapsing header: the enable checkbox and drag controls for exposure, contrast, saturation, brightness, temperature, tint, gamma, and LUT intensity. Everything takes effect immediately. The LUT texture itself isn't pickable from the editor; set it in code.

---

## Limitations

**Output is clamped to LDR.** The grade writes `[0, 1]` color. Anything that needs HDR data has to run before it, which is why it sits after bloom in the default order.

**No per-channel curves or color wheels.** The controls are global scalar operations plus the LUT. If you need lift/gamma/gain per channel or split toning, bake it into a LUT.

**LUT colors are clamped before lookup.** HDR values above `1.0` all land on the LUT's edge, so a LUT can't distinguish "bright" from "very bright". Use exposure to bring the range in first.

**Disabled under Iris.** Like all Amnetic screen passes, the grade skips itself when Iris is loaded; use your shaderpack's own grading instead.

---

## Reference

### `ColorGrade`

```java
ColorGradeSettings settings(); // the one global settings instance
void enable(); // settings().enabled(true)
void disable(); // settings().enabled(false)
void render(); // pre-registered at POST 20
void dispose(); // free the capture buffer and program
```

You don't normally call `render()` or `dispose()` yourself; Amnetic registers both.

### `ColorGradeSettings`

All setters return `this` and chain.

```java
ColorGradeSettings enabled(boolean v);      boolean isEnabled(); // default false
ColorGradeSettings exposure(float v);       float exposure(); // default 1.0, clamp >= 0
ColorGradeSettings contrast(float v);       float contrast(); // default 1.0, clamp >= 0
ColorGradeSettings saturation(float v);     float saturation(); // default 1.0, clamp >= 0
ColorGradeSettings brightness(float v);     float brightness(); // default 0.0, unclamped
ColorGradeSettings temperature(float v);    float temperature(); // default 0.0, clamp [-1, 1]
ColorGradeSettings tint(float v);           float tint(); // default 0.0, clamp [-1, 1]
ColorGradeSettings gamma(float v);          float gamma(); // default 1.0, clamp >= 0.01
ColorGradeSettings lut(Identifier id);      Identifier lut(); // default null
ColorGradeSettings lutSize(int v);          int lutSize(); // default 16, clamp >= 2
ColorGradeSettings lutIntensity(float v);   float lutIntensity(); // default 1.0, clamp [0, 1]
```

---

## See Also

- [Render Pipeline](Render-Pipeline). Where the grade pass is registered and how to slot your own around it.
- [TAA](TAA). The resolve runs before the grade; the CAS sharpen runs after it.
- [Bloom](Bloom). Composites in HDR just before the grade clamps to LDR.
- [Editor](Editor). The Post FX inspector with the live grading controls.
