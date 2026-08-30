# Light Styles

A light style is a GLSL snippet that hooks into how a single `Light` is evaluated in the deferred lighting pass. Where a [shading model](Shading-Models) customises how a *surface* responds to light, a light style customises the *light itself*: where it appears to come from, how it attenuates, and what colour it contributes.

That makes it the tool for effects the built-in falloff curves cannot express: a light that flickers on a noise field, one that projects a moving caustic pattern, one that wraps around a corner, or one whose apparent position drifts from its real one.

```java
import com.meekdev.amnetic.client.light.LightStyles;
import com.meekdev.amnetic.client.light.Lights;
import com.meekdev.amnetic.client.light.Light;

int flicker = LightStyles.register("flicker", """
        if (stage == AMNETIC_STYLE_SHADE) {
            float n = fract(sin(dot(lightPos.xz, vec2(12.9898, 78.233))) * 43758.5453);
            atten *= 0.7 + 0.3 * sin(n * 40.0);
        }
        """);

Light torch = Lights.point(x, y, z).range(12f).style(flicker);
```

`style(0)` is the default and means Amnetic's own lighting, untouched.

## The hook

The registry generates a virtual GLSL include, `amnetic:shaders/light/styles.glsl`. There is no file on disk. It resolves to a single dispatch function that the deferred lighting shader calls:

```glsl
void amneticLightStyle(int style, int stage,
                       inout vec3 p, inout float atten, inout vec3 color,
                       vec3 lightPos, vec3 lightDir);
```

Your snippet is spliced into an `if (style == N) { ... }` branch inside it. Registering a style marks every shader dirty and the deferred pass relinks on the next frame, so registration is safe at any time. Re-registering the same *name* replaces the body and keeps the ID stable, which is what makes styles work with shader hot reload.

## The two stages

The hook is called twice per light, and `stage` tells you which call you are in. This matters because the two stages can write different things.

| Stage | When | Writable |
|---|---|---|
| `AMNETIC_STYLE_POINT` | Before the light vector and attenuation are computed | `p` only |
| `AMNETIC_STYLE_SHADE` | After attenuation and cookie tinting, before shadowing | `atten` and `color` |

At `AMNETIC_STYLE_POINT`, `p` starts as the fragment position and everything downstream is computed from whatever you leave in it. Displacing it is how you move where the light *appears* to fall without moving the `Light` itself.

At `AMNETIC_STYLE_SHADE`, `atten` holds the computed falloff and `color` the light's colour after any cookie. Writing them scales or tints this light's contribution. Setting `atten` to zero or less makes the light contribute nothing to this fragment and the shader returns early, which is the cheap way to mask a light to a region.

`lightPos` and `lightDir` are the light's real world position and direction, camera-relative, and are read-only in both stages.

## Parameters

| Name | Type | Meaning |
|---|---|---|
| `style` | `int` | The style ID being dispatched. You do not normally read it |
| `stage` | `int` | `AMNETIC_STYLE_POINT` or `AMNETIC_STYLE_SHADE` |
| `p` | `inout vec3` | Sample position. Writable at the point stage |
| `atten` | `inout float` | Attenuation. Writable at the shade stage |
| `color` | `inout vec3` | Light colour. Writable at the shade stage |
| `lightPos` | `vec3` | The light's world position, camera-relative |
| `lightDir` | `vec3` | The light's direction |

## Managing styles

```java
LightStyles.register(name, glslBody);  // returns the id, replaces an existing name in place
LightStyles.id(name);                  // the id for a name, or null
LightStyles.clear();                   // drop every style and relink
```

IDs start at 1 and are assigned in registration order. They are runtime values, so look them up by name rather than hard-coding a number.

The style ID rides in the light's own data lane, not the G-buffer, so unlike shading models there is no 255-ID ceiling and styles apply to any light regardless of what geometry it hits.

## Limitations

* Snippets are not validated before splicing. A syntax error in one style breaks the deferred lighting shader, which disables deferred lighting until it is fixed.
* The hook runs per light per fragment. An expensive snippet on a light with a wide range is a per-pixel cost across everything it touches.
* Writes to `atten` and `color` at the point stage are discarded, and writes to `p` at the shade stage arrive too late to affect the light vector.
* Styles run before shadowing, so a style cannot see or override the shadow result.
* `LightStyles.touch()` exists so the class loads and registers its hook before the first shader link. `AmneticClient` calls it during init; you only need it if you link a shader before ever touching the class yourself.

## See Also

* [Deferred Lights](Deferred-Lights) for `Lights`, `Light`, and the falloff curves a style modifies
* [Shadows](Shadows) for the shadowing that runs after your style
* [Shading Models](Shading-Models) for the surface-side counterpart
* [Shader Hot Reload](Shader-Hot-Reload) for iterating on a style live
